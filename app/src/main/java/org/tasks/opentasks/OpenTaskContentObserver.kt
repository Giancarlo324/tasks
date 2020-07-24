package org.tasks.opentasks

import android.content.Context
import android.content.UriMatcher
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import org.dmfs.tasks.contract.TaskContract.*
import org.tasks.R
import org.tasks.jobs.WorkManager
import org.tasks.preferences.PermissionChecker
import timber.log.Timber
import javax.inject.Inject

class OpenTaskContentObserver @Inject constructor(
        @ApplicationContext context: Context,
        private val workManager: WorkManager) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        listOf("org.dmfs.tasks", context.getString(R.string.opentasks_authority)).forEach {
            addURI(it, "tasks/*", URI_TASKS)
            addURI(it, "tasklists/*", URI_TASK_LISTS)
        }
    }

    override fun onChange(selfChange: Boolean) = onChange(selfChange, null)

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        if (selfChange || uri == null) {
            Timber.d("Ignoring onChange(selfChange = $selfChange, uri = $uri)")
            return
        }

        when (matcher.match(uri)) {
            URI_TASKS -> {
                uri.lastPathSegment
                        ?.toLongOrNull()
                        ?.let { workManager.sync(taskId = it, listId = null) }
                        ?: Timber.e("Invalid uri: $uri")
            }
            URI_TASK_LISTS -> {
                uri.lastPathSegment
                        ?.toLongOrNull()
                        ?.let { workManager.sync(taskId = null, listId = it) }
                        ?: Timber.e("Invalid uri: $uri")
            }
            else -> Timber.d("Ignoring onChange(selfChange = $selfChange, uri = $uri)")
        }
    }

    companion object {
        private const val URI_TASKS = 0
        private const val URI_TASK_LISTS = 1

        fun registerObserver(context: Context, observer: ContentObserver) {
            getUris(context.getString(R.string.opentasks_authority))
                    .plus(if (PermissionChecker(context).canAccessOpenTasks()) {
                        getUris("org.dmfs.tasks")
                    } else {
                        emptyList()
                    })
                    .forEach {
                        context.contentResolver.registerContentObserver(it, true, observer)
                    }
        }

        private fun getUris(authority: String): List<Uri> =
                listOf(TaskLists.getContentUri(authority),
                        Tasks.getContentUri(authority),
                        Properties.getContentUri(authority))
    }
}