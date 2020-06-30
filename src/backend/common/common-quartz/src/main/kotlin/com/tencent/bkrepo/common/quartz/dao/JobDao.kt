package com.tencent.bkrepo.common.quartz.dao

import com.mongodb.MongoWriteException
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.result.DeleteResult
import com.tencent.bkrepo.common.quartz.converter.JobConverter
import com.tencent.bkrepo.common.quartz.util.GroupHelper
import com.tencent.bkrepo.common.quartz.util.Keys
import com.tencent.bkrepo.common.quartz.util.Keys.KEY_GROUP
import com.tencent.bkrepo.common.quartz.util.Keys.toFilter
import com.tencent.bkrepo.common.quartz.util.QueryHelper
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.JobPersistenceException
import org.quartz.impl.matchers.GroupMatcher

class JobDao(
    private val jobCollection: MongoCollection<Document>,
    private val jobConverter: JobConverter
) {
    private val groupHelper = GroupHelper(jobCollection)

    fun clear(): DeleteResult {
        return jobCollection.deleteMany(Document())
    }

    fun createIndex() {
        jobCollection.createIndex(Keys.KEY_AND_GROUP_FIELDS, IndexOptions().unique(true))
    }

    fun exists(jobKey: JobKey): Boolean {
        return jobCollection.count(Keys.toFilter(jobKey)) > 0
    }

    fun getById(id: Any): Document? {
        return jobCollection.find(Filters.eq("_id", id)).first()
    }

    fun getJob(keyObject: Bson): Document? {
        return jobCollection.find(keyObject).first()
    }

    fun getJob(key: JobKey): Document? {
        return getJob(toFilter(key))
    }

    fun getCount(): Long = jobCollection.count()

    fun getGroupNames(): MutableList<String> {
        return jobCollection.distinct(KEY_GROUP, String::class.java).into(mutableListOf())
    }

    fun getJobKeys(matcher: GroupMatcher<JobKey>): MutableSet<JobKey> {
        val query = QueryHelper.matchingKeysConditionFor(matcher)
        return jobCollection.find(query).projection(Keys.KEY_AND_GROUP_FIELDS)
            .map { Keys.toJobKey(it) }
            .toMutableSet()

    }

    fun idsOfMatching(matcher: GroupMatcher<JobKey>): MutableCollection<ObjectId> {
        return findMatching(matcher).map { it.getObjectId("_id") }.toMutableList()
    }

    fun remove(keyObject: Bson) {
        jobCollection.deleteMany(keyObject)
    }

    fun requestsRecovery(jobKey: JobKey): Boolean {
        //TODO check if it's the same as getJobDataMap?
        val jobDoc: Document = getJob(jobKey) ?: return false
        return jobDoc.getBoolean(JobConverter.JOB_REQUESTS_RECOVERY, false)
    }

    fun retrieveJob(jobKey: JobKey): JobDetail? {
        val doc: Document = getJob(jobKey) ?: return null
        return jobConverter.toJobDetail(doc)
    }

    @Throws(JobPersistenceException::class)
    fun storeJobInMongo(newJob: JobDetail, replaceExisting: Boolean): ObjectId {
        val key = newJob.key
        val keyDbo = toFilter(key)
        val job = jobConverter.toDocument(newJob, key)
        val existedJob = getJob(keyDbo)
        return if (existedJob != null) {
            if (replaceExisting) {
                jobCollection.replaceOne(keyDbo, job)
                job.getObjectId("_id")
            } else {
                existedJob.getObjectId("_id")
            }
        } else {
            try {
                jobCollection.insertOne(job)
                job.getObjectId("_id")
            } catch (e: MongoWriteException) {
                getJob(keyDbo)!!.getObjectId("_id")
            }
        }
    }

    private fun findMatching(matcher: GroupMatcher<JobKey>): MutableCollection<Document> {
        return groupHelper.inGroupsThatMatch(matcher)
    }

}
