package com.tencent.bkrepo.common.quartz.dao

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.client.model.UpdateOptions
import com.tencent.bkrepo.common.quartz.cluster.Scheduler
import com.tencent.bkrepo.common.quartz.util.Clock
import org.bson.Document
import org.bson.conversions.Bson
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SchedulerDao(
    val schedulerCollection: MongoCollection<Document>,
    val schedulerName: String,
    val instanceId: String,
    private val clusterCheckinIntervalMillis: Long,
    private val clock: Clock
) {
    private val schedulerFilter = createSchedulerFilter(schedulerName, instanceId)

    fun createIndex() {
        schedulerCollection.createIndex(
            Projections.include(SCHEDULER_NAME_FIELD, INSTANCE_ID_FIELD),
            IndexOptions().unique(true)
        )
    }

    /**
     * Checks-in in cluster to inform other nodes that its alive.
     */
    fun checkIn() {
        val lastCheckinTime: Long = clock.millis()
        log.debug(
            "Saving node data: name='{}', id='{}', checkin time={}, interval={}",
            schedulerName, instanceId, lastCheckinTime, clusterCheckinIntervalMillis
        )

        // If not found Mongo will create a new entry with content from filter and update.
        val update = createUpdateClause(lastCheckinTime)
        val result = schedulerCollection.updateOne(schedulerFilter, update, UpdateOptions().upsert(true))
        log.debug("Node {}:{} check-in result: {}", schedulerName, instanceId, result)
    }

    /**
     * @return Scheduler or null when not found
     */
    fun findInstance(instanceId: String): Scheduler? {
        log.debug("Finding scheduler instance: {}", instanceId)
        val doc: Document? = schedulerCollection
            .find(createSchedulerFilter(schedulerName, instanceId))
            .first()
        return if (doc != null) {
            val scheduler = toScheduler(doc)
            log.debug("Returning scheduler instance '{}' with last checkin time: {}", scheduler.instanceId, scheduler.lastCheckinTime)
            scheduler
        } else {
            log.info("Scheduler instance '{}' not found.")
            null
        }
    }

    fun isNotSelf(scheduler: Scheduler?): Boolean {
        return instanceId != scheduler?.instanceId
    }

    /**
     * Return all scheduler instances in ascending order by last check-in time.
     *
     * @return schedler instances ordered by last check-in time
     */
    fun getAllByCheckinTime(): MutableList<Scheduler> {
        return schedulerCollection.find()
            .sort(ascending(LAST_CHECKIN_TIME_FIELD))
            .map { toScheduler(it) }
            .toMutableList()
    }

    /**
     * Remove selected scheduler instance entry from database.
     *
     * The scheduler is selected based on its name, instanceId, and lastCheckinTime.
     * If the last check-in time is different, then it is not removed, for it might
     * have gotten back to live.
     *
     * @param instanceId       instance id
     * @param lastCheckinTime  last time scheduler has checked in
     *
     * @return when removed successfully
     */
    fun remove(instanceId: String, lastCheckinTime: Long): Boolean {
        log.debug("Removing scheduler: {},{},{}", schedulerName, instanceId, lastCheckinTime)
        val filter = createSchedulerFilter(schedulerName, instanceId, lastCheckinTime)
        val result = schedulerCollection.deleteOne(filter)
        log.debug("Result of removing scheduler ({},{},{}): {}", schedulerName, instanceId, lastCheckinTime, result)
        return result.deletedCount == 1L
    }

    private fun createSchedulerFilter(schedulerName: String, instanceId: String, lastCheckinTime: Long): Bson {
        return Filters.and(
            Filters.eq(SCHEDULER_NAME_FIELD, schedulerName),
            Filters.eq(INSTANCE_ID_FIELD, instanceId),
            Filters.eq(LAST_CHECKIN_TIME_FIELD, lastCheckinTime)
        )
    }

    private fun createSchedulerFilter(schedulerName: String, instanceId: String): Bson {
        return Filters.and(
            Filters.eq(SCHEDULER_NAME_FIELD, schedulerName),
            Filters.eq(INSTANCE_ID_FIELD, instanceId)
        )
    }

    private fun createUpdateClause(lastCheckinTime: Long): Document {
        return Document(
            "\$set", Document()
                .append(LAST_CHECKIN_TIME_FIELD, lastCheckinTime)
                .append(CHECKIN_INTERVAL_FIELD, clusterCheckinIntervalMillis)
        )
    }

    private fun toScheduler(document: Document): Scheduler {
        return Scheduler(
            document.getString(SCHEDULER_NAME_FIELD),
            document.getString(INSTANCE_ID_FIELD),
            document.getLong(LAST_CHECKIN_TIME_FIELD),
            document.getLong(CHECKIN_INTERVAL_FIELD)
        )
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SchedulerDao::class.java)
        const val SCHEDULER_NAME_FIELD: String = "schedulerName"
        const val INSTANCE_ID_FIELD: String = "instanceId"
        const val LAST_CHECKIN_TIME_FIELD: String = "lastCheckinTime"
        const val CHECKIN_INTERVAL_FIELD: String = "checkinInterval"
    }

}
