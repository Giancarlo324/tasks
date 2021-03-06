package org.tasks.data

import androidx.lifecycle.LiveData
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@Deprecated("use coroutines")
class TagDataDaoBlocking @Inject constructor(private val dao: TagDataDao) {
    fun subscribeToTags(): LiveData<List<TagData>> {
        return dao.subscribeToTags()
    }

    fun tagDataOrderedByName(): List<TagData> = runBlocking {
        dao.tagDataOrderedByName()
    }
}