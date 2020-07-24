package org.tasks.jobs

import android.content.Context
import androidx.hilt.Assisted
import androidx.hilt.work.WorkerInject
import androidx.work.WorkerParameters
import org.tasks.LocalBroadcastManager
import org.tasks.analytics.Firebase
import org.tasks.injection.BaseWorker
import org.tasks.opentasks.OpenTasksSynchronizer
import timber.log.Timber

class OpenTaskWork @WorkerInject constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        firebase: Firebase,
        private val openTasksSynchronizer: OpenTasksSynchronizer,
        private val localBroadcastManager: LocalBroadcastManager
) : BaseWorker(context, workerParams, firebase) {
    
    override suspend fun run(): Result {
        inputData.getLong(EXTRA_TASK_ID, -1).takeIf { it >= 0 }?.let { syncTask(it) }
        inputData.getLong(EXTRA_LIST_ID, -1).takeIf { it >= 0 }?.let { syncList(it) }
        return Result.success()
    }

    private suspend fun syncTask(id: Long) {
        Timber.d("syncTask($id)")
        try {
            openTasksSynchronizer.sync(id)
            localBroadcastManager.broadcastRefresh()
        } catch (e: Exception) {
            firebase.reportException(e)
        }
    }

    private suspend fun syncList(id: Long) {
        Timber.d("syncList($id)")
        try {
            openTasksSynchronizer.syncList(id)
            localBroadcastManager.broadcastRefreshList()
        } catch (e: Exception) {
            firebase.reportException(e)
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_LIST_ID = "extra_work_id"
    }
}