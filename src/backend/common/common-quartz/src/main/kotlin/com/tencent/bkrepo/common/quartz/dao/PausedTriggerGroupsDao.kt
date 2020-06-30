package com.tencent.bkrepo.common.quartz.dao

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.novemberain.quartz.mongodb.util.Keys.KEY_GROUP
import org.bson.Document
import java.util.HashSet

class PausedTriggerGroupsDao(
    private val triggerGroupsCollection: MongoCollection<Document>
) {
    fun getPausedGroups(): HashSet<String> {
        return triggerGroupsCollection.distinct(KEY_GROUP, String::class.java).toHashSet()
    }

    fun pauseGroups(groups: Collection<String>) {
        val list = groups.map { Document(KEY_GROUP, it) }
        triggerGroupsCollection.insertMany(list)
    }

    fun remove() {
        triggerGroupsCollection.deleteMany(Document())
    }

    fun unpauseGroups(groups: Collection<String>) {
        triggerGroupsCollection.deleteMany(Filters.`in`(KEY_GROUP, groups))
    }
}
