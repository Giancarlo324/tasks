package org.tasks.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import at.bitfire.ical4android.UnknownProperty
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.fortuna.ical4j.model.property.XProperty
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Category
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.json.JSONObject
import org.tasks.R
import org.tasks.caldav.iCalendar.Companion.APPLE_SORT_ORDER
import org.tasks.preferences.PermissionChecker
import timber.log.Timber
import javax.inject.Inject

class OpenTaskDao @Inject constructor(
        @ApplicationContext context: Context,
        permissionChecker: PermissionChecker) {

    private val cr = context.contentResolver
    val authority = if (permissionChecker.canAccessOpenTasks()) {
        "org.dmfs.tasks"
    } else {
        context.getString(R.string.opentasks_authority)
    }

    suspend fun accountCount(): Int = getLists().map { it.account!! }.toHashSet().size

    suspend fun getLists(): List<CaldavCalendar> = withContext(Dispatchers.IO) {
        val calendars = ArrayList<CaldavCalendar>()
        cr.query(
                TaskContract.TaskLists.getContentUri(authority),
                null,
                "${TaskContract.TaskListColumns.SYNC_ENABLED}=1 AND (${TaskContract.ACCOUNT_TYPE} = 'bitfire.at.davdroid' OR ${TaskContract.ACCOUNT_TYPE} = 'com.etesync.syncadapter')",
                null,
                null)?.use {
            while (it.moveToNext()) {
                val accountType = it.getString(TaskContract.TaskLists.ACCOUNT_TYPE)
                val accountName = it.getString(TaskContract.TaskLists.ACCOUNT_NAME)
                calendars.add(CaldavCalendar().apply {
                    id = it.getLong(TaskContract.TaskLists._ID)
                    account = "$accountType:$accountName"
                    name = it.getString(TaskContract.TaskLists.LIST_NAME)
                    color = it.getInt(TaskContract.TaskLists.LIST_COLOR)
                    url = it.getString(TaskContract.CommonSyncColumns._SYNC_ID)
                    ctag = it.getString(TaskContract.TaskLists.SYNC_VERSION)
                            ?.let(::JSONObject)
                            ?.getString("value")
                })
            }
        }
        calendars
    }

    suspend fun getEtags(listId: Long): List<Pair<String, String?>> = withContext(Dispatchers.IO) {
        val items = ArrayList<Pair<String, String?>>()
        cr.query(
                Tasks.getContentUri(authority),
                arrayOf(Tasks._SYNC_ID, Tasks.SYNC1),
                "${Tasks.LIST_ID} = $listId",
                null,
                null)?.use {
            while (it.moveToNext()) {
                Pair(it.getString(Tasks._SYNC_ID)!!, it.getString(Tasks.SYNC1)).let(items::add)
            }
        }
        items
    }

    suspend fun delete(listId: Long, item: String): Int = withContext(Dispatchers.IO) {
        cr.delete(
                Tasks.getContentUri(authority),
                "${Tasks.LIST_ID} = $listId AND ${Tasks._SYNC_ID} = '$item'",
                null)
    }

    suspend fun getId(uid: String?): Long =
            uid?.let {
                withContext(Dispatchers.IO) {
                    cr.query(
                            Tasks.getContentUri(authority),
                            arrayOf(Tasks._ID),
                            "${Tasks._UID} = '$uid'",
                            null,
                            null)?.use {
                        if (it.moveToFirst()) {
                            it.getLong(Tasks._ID)
                        } else {
                            Timber.e("No task with uid=$uid")
                            null
                        }
                    }
                }
            } ?: 0L

    suspend fun getUid(id: Long): String? = if (id == 0L) {
        null
    } else {
        withContext(Dispatchers.IO) {
            cr.query(
                    Tasks.getContentUri(authority),
                    arrayOf(Tasks._UID),
                    "${Tasks._ID} = $id",
                    null,
                    null)?.use {
                if (it.moveToFirst()) {
                    it.getString(Tasks._UID)
                } else {
                    Timber.e("No task with id=$id")
                    null
                }
            }
        }
    }

    suspend fun getTags(caldavTask: CaldavTask): List<String> = withContext(Dispatchers.IO) {
        val id = getId(caldavTask.remoteId)
        val tags = ArrayList<String>()
        cr.query(
                Properties.getContentUri(authority),
                arrayOf(Properties.DATA1),
                "${Properties.TASK_ID} = $id AND ${Properties.MIMETYPE} = '${Category.CONTENT_ITEM_TYPE}'",
                null,
                null)?.use {
            while (it.moveToNext()) {
                it.getString(Properties.DATA1)?.let(tags::add)
            }
        }
        return@withContext tags
    }

    suspend fun setTags(caldavTask: CaldavTask, tags: List<String>) = withContext(Dispatchers.IO) {
        val id = getId(caldavTask.remoteId)
        cr.delete(
                Properties.getContentUri(authority),
                "${Properties.TASK_ID} = $id AND ${Properties.MIMETYPE} = '${Category.CONTENT_ITEM_TYPE}'",
                null)
        tags.forEach {
            cr.insert(Properties.getContentUri(authority), ContentValues().apply {
                put(Category.MIMETYPE, Category.CONTENT_ITEM_TYPE)
                put(Category.TASK_ID, id)
                put(Category.CATEGORY_NAME, it)
            })
        }
    }

    suspend fun getRemoteOrder(caldavTask: CaldavTask): Long? = withContext(Dispatchers.IO) {
        val id = getId(caldavTask.remoteId)
        cr.query(
                Properties.getContentUri(authority),
                arrayOf(Properties.DATA0),
                "${Properties.TASK_ID} = $id AND ${Properties.MIMETYPE} = '${UnknownProperty.CONTENT_ITEM_TYPE}' AND ${Properties.DATA0} LIKE '%$APPLE_SORT_ORDER%'",
                null,
                null)?.use {
            while (it.moveToNext()) {
                it.getString(Properties.DATA0)
                        ?.let(UnknownProperty::fromJsonString)
                        ?.takeIf { xprop -> xprop.name.equals(APPLE_SORT_ORDER, true) }
                        ?.let { xprop ->
                            return@withContext xprop.value.toLong()
                        }
            }
        }
        return@withContext null
    }

    suspend fun setRemoteOrder(caldavTask: CaldavTask) = withContext(Dispatchers.IO) {
        val id = getId(caldavTask.remoteId)
        cr.delete(
                Properties.getContentUri(authority),
                "${Properties.TASK_ID} = $id AND ${Properties.MIMETYPE} = '${UnknownProperty.CONTENT_ITEM_TYPE}' AND ${Properties.DATA0} LIKE '%$APPLE_SORT_ORDER%'",
                null)
        caldavTask.order?.let {
            cr.insert(Properties.getContentUri(authority), ContentValues().apply {
                put(Properties.MIMETYPE, UnknownProperty.CONTENT_ITEM_TYPE)
                put(Properties.TASK_ID, id)
                put(Properties.DATA0, UnknownProperty.toJsonString(XProperty(APPLE_SORT_ORDER, it.toString())))
            })
        }
    }

    suspend fun updateParent(caldavTask: CaldavTask) = withContext(Dispatchers.IO) {
        caldavTask.remoteParent
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    cr.insert(Properties.getContentUri(authority), ContentValues().apply {
                        put(Relation.MIMETYPE, Relation.CONTENT_ITEM_TYPE)
                        put(Relation.TASK_ID, getId(caldavTask.remoteId))
                        put(Relation.RELATED_TYPE, Relation.RELTYPE_PARENT)
                        put(Relation.RELATED_ID, getId(caldavTask.remoteParent))
                    })
                }
    }

    companion object {
        fun Cursor.getString(columnName: String): String? =
                getString(getColumnIndex(columnName))

        fun Cursor.getInt(columnName: String): Int =
                getInt(getColumnIndex(columnName))

        fun Cursor.getLong(columnName: String): Long =
                getLong(getColumnIndex(columnName))
    }
}