package com.tencent.bkrepo.common.quartz.util

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.novemberain.quartz.mongodb.util.Keys.KEY_GROUP
import org.bson.Document
import org.bson.types.ObjectId
import java.util.LinkedList

class TriggerGroupHelper(
    private val collection: MongoCollection<Document>
) : GroupHelper(collection) {
    fun groupsForJobId(jobId: ObjectId): List<String> {
        return collection.distinct(KEY_GROUP, String::class.java)
            .filter(Filters.eq<Any>(JOB_ID, jobId))
            .into(LinkedList<String>())
    }

    fun groupsForJobIds(ids: Collection<ObjectId>?): List<String> {
        return collection.distinct(KEY_GROUP, String::class.java)
            .filter(Filters.`in`<Collection<ObjectId>>(JOB_ID, ids))
            .toList()
    }

    companion object {
        const val JOB_ID = "jobId"
    }
}
