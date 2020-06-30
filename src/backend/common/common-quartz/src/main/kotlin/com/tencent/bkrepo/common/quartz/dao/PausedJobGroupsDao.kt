package com.tencent.bkrepo.common.quartz.dao

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.novemberain.quartz.mongodb.util.Keys.KEY_GROUP
import org.bson.Document
import java.util.HashSet

class PausedJobGroupsDao(
    private val pausedJobGroupsCollection: MongoCollection<Document>
) {
    fun getPausedGroups(): HashSet<String> {
        return pausedJobGroupsCollection.distinct(KEY_GROUP, String::class.java).toHashSet()
    }

    fun pauseGroups(groups: List<String>) {
        val list = groups.map { Document(KEY_GROUP, it) }
        pausedJobGroupsCollection.insertMany(list)
    }

    fun remove() {
        pausedJobGroupsCollection.deleteMany(Document())
    }

    fun unpauseGroups(groups: Collection<String>) {
        pausedJobGroupsCollection.deleteMany(Filters.`in`(KEY_GROUP, groups))
    }

}
