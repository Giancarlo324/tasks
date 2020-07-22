package org.tasks.opentasks

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.dmfs.tasks.contract.TaskContract.*
import org.tasks.R
import org.tasks.jobs.WorkManager
import org.tasks.preferences.PermissionChecker
import org.tasks.preferences.Preferences
import timber.log.Timber
import javax.inject.Inject

class OpenTaskContentObserver @Inject constructor(
        private val preferences: Preferences,
        private val workManager: WorkManager) : ContentObserver(Handler(Looper.getMainLooper())) {

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        Timber.d("${Thread.currentThread().name}: onChange($selfChange, $uri)")

        if (preferences.isSyncOngoing) {
            Timber.d("Sync ongoing, ignoring change")
            return
        }

        if (!selfChange) {
            Timber.d("Requesting synchronization")
            workManager.sync(false)
        }
    }

    companion object {
        fun registerObserver(context: Context, observer: ContentObserver) {
            getUris(context.getString(R.string.opentasks_authority))
                    .plus(if (PermissionChecker(context).canAccessOpenTasks()) {
                        getUris("org.dmfs.tasks")
                    } else {
                        emptyList()
                    })
                    .forEach {
                        context.contentResolver.registerContentObserver(it, false, observer)
                    }
        }

        private fun getUris(authority: String): List<Uri> =
                listOf(TaskLists.getContentUri(authority),
                        Tasks.getContentUri(authority),
                        Properties.getContentUri(authority))
    }
}