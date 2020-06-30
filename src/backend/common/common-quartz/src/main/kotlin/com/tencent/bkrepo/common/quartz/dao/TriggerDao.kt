package com.tencent.bkrepo.common.quartz.dao

import com.mongodb.MongoWriteException
import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.client.model.UpdateOptions
import com.tencent.bkrepo.common.quartz.STATE_WAITING
import com.tencent.bkrepo.common.quartz.TRIGGER_JOB_ID
import com.tencent.bkrepo.common.quartz.TRIGGER_NEXT_FIRE_TIME
import com.tencent.bkrepo.common.quartz.TRIGGER_STATE
import com.tencent.bkrepo.common.quartz.converter.TriggerConverter
import com.tencent.bkrepo.common.quartz.util.Keys
import com.tencent.bkrepo.common.quartz.util.Keys.KEY_GROUP
import com.tencent.bkrepo.common.quartz.util.Keys.toFilter
import com.tencent.bkrepo.common.quartz.util.QueryHelper
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.quartz.JobPersistenceException
import org.quartz.ObjectAlreadyExistsException
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.impl.matchers.GroupMatcher
import org.quartz.spi.OperableTrigger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Date

class TriggerDao(
    val triggerCollection: MongoCollection<Document>,
    private val triggerConverter: TriggerConverter
) {
    fun createIndex() {
        triggerCollection.createIndex(Keys.KEY_AND_GROUP_FIELDS, IndexOptions().unique(true))
    }

    fun clear() {
        triggerCollection.deleteMany(Document())
    }

    fun exists(filter: Bson): Boolean {
        return triggerCollection.count(filter) > 0
    }

    fun findEligibleToRun(noLaterThanDate: Date): FindIterable<Document> {
        val query = createNextTriggerQuery(noLaterThanDate)
        if (logger.isDebugEnabled) {
            logger.debug("Found {} triggers which are eligible to be run.", getCount(query))
        }
        return triggerCollection.find(query).sort(ascending(TRIGGER_NEXT_FIRE_TIME))
    }

    fun findTrigger(filter: Bson): Document? {
        return triggerCollection.find(filter).first()
    }

    fun getCount(): Long {
        return triggerCollection.count()
    }

    fun getGroupNames(): List<String> {
        return triggerCollection.distinct(KEY_GROUP, String::class.java).toList()
    }

    fun getState(triggerKey: TriggerKey): String? {
        val doc = findTrigger(triggerKey) ?: return null
        return doc.getString(TRIGGER_STATE)
    }

    @Throws(JobPersistenceException::class)
    fun getTrigger(triggerKey: TriggerKey): OperableTrigger? {
        val doc = findTrigger(toFilter(triggerKey)) ?: return null
        return triggerConverter.toTrigger(triggerKey, doc)
    }

    @Throws(JobPersistenceException::class)
    fun getTriggersForJob(doc: Document): List<OperableTrigger> {
        return findByJobId(doc["_id"]!!).map {
            triggerConverter.toTrigger(it)
        }.filterNotNull().toList()
    }

    fun getTriggerKeys(matcher: GroupMatcher<TriggerKey>): Set<TriggerKey> {
        val query: Bson = QueryHelper.matchingKeysConditionFor(matcher)
        return triggerCollection.find(query).projection(Keys.KEY_AND_GROUP_FIELDS).map {
            Keys.toTriggerKey(it)
        }.toSet()
    }

    fun hasLastTrigger(job: Document): Boolean {
        val referencedTriggers: MutableList<Document?> = triggerCollection
            .find(Filters.eq(TRIGGER_JOB_ID, job["_id"]))
            .limit(2)
            .into(ArrayList(2))
        return referencedTriggers.size == 1
    }

    @Throws(ObjectAlreadyExistsException::class)
    fun insert(trigger: Document, offendingTrigger: Trigger) {
        try {
            triggerCollection.insertOne(trigger)
        } catch (key: MongoWriteException) {
            throw ObjectAlreadyExistsException(offendingTrigger)
        }
    }

    fun remove(filter: Bson) {
        triggerCollection.deleteMany(filter)
    }

    fun remove(triggerKey: TriggerKey) {
        remove(toFilter(triggerKey))
    }

    fun removeByJobId(id: Any) {
        triggerCollection.deleteMany(Filters.eq(TRIGGER_JOB_ID, id))
    }

    fun replace(triggerKey: TriggerKey, trigger: Document) {
        triggerCollection.replaceOne(toFilter(triggerKey), trigger, UpdateOptions().upsert(true))
    }

    fun setState(triggerKey: TriggerKey, state: String) {
        triggerCollection.updateOne(toFilter(triggerKey), createTriggerStateUpdateDocument(state))
    }

    fun transferState(triggerKey: TriggerKey, oldState: String, newState: String) {
        triggerCollection.updateOne(
            Filters.and(toFilter(triggerKey), Filters.eq(TRIGGER_STATE, oldState)),
            createTriggerStateUpdateDocument(newState)
        )
    }

    fun setStateInAll(state: String) {
        setStates(Document(), state)
    }

    fun setStateByJobId(jobId: ObjectId, state: String) {
        setStates(Document(TRIGGER_JOB_ID, jobId), state)
    }

    fun setStateInGroups(groups: List<String>, state: String) {
        setStates(QueryHelper.inGroups(groups), state)
    }

    fun setStateInMatching(matcher: GroupMatcher<TriggerKey>, state: String) {
        setStates(matcher, state)
    }

    private fun createNextTriggerQuery(noLaterThanDate: Date): Bson {
        return Filters.and(
            Filters.or(
                Filters.eq(TRIGGER_NEXT_FIRE_TIME, null),
                Filters.lte(TRIGGER_NEXT_FIRE_TIME, noLaterThanDate)
            ),
            Filters.eq(TRIGGER_STATE, STATE_WAITING)
        )
    }

    private fun createTriggerStateUpdateDocument(state: String): Bson {
        return Document("\$set", Document(TRIGGER_STATE, state))
    }

    private fun findByJobId(jobId: Any): FindIterable<Document> {
        return triggerCollection.find(Filters.eq(TRIGGER_JOB_ID, jobId))
    }

    private fun findTrigger(key: TriggerKey): Document? {
        return findTrigger(toFilter(key))
    }

    private fun getCount(query: Bson): Long {
        return triggerCollection.count(query)
    }

    private fun setStates(filter: Bson, state: String) {
        triggerCollection.updateMany(filter, createTriggerStateUpdateDocument(state))
    }

    private fun setStates(matcher: GroupMatcher<TriggerKey>, state: String) {
        triggerCollection.updateMany(
            QueryHelper.matchingKeysConditionFor(matcher),
            createTriggerStateUpdateDocument(state),
            UpdateOptions().upsert(false)
        )
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(TriggerDao::class.java)
    }

}
