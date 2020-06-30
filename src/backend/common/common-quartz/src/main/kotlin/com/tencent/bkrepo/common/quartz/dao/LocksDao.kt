package com.tencent.bkrepo.common.quartz.dao

import com.mongodb.MongoException
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Projections
import com.mongodb.client.result.UpdateResult
import com.novemberain.quartz.mongodb.Constants.LOCK_INSTANCE_ID
import com.novemberain.quartz.mongodb.util.Keys.KEY_GROUP
import com.novemberain.quartz.mongodb.util.Keys.KEY_NAME
import com.novemberain.quartz.mongodb.util.Keys.LOCK_TYPE
import com.novemberain.quartz.mongodb.util.Keys.createJobLock
import com.novemberain.quartz.mongodb.util.Keys.createJobLockFilter
import com.novemberain.quartz.mongodb.util.Keys.createLockUpdateDocument
import com.novemberain.quartz.mongodb.util.Keys.createRelockFilter
import com.novemberain.quartz.mongodb.util.Keys.createTriggerLock
import com.novemberain.quartz.mongodb.util.Keys.createTriggerLockFilter
import com.novemberain.quartz.mongodb.util.Keys.createTriggersLocksFilter
import com.novemberain.quartz.mongodb.util.Keys.toTriggerKey
import com.tencent.bkrepo.common.quartz.util.Clock
import com.tencent.bkrepo.common.quartz.util.Keys.toFilter
import org.bson.Document
import org.bson.conversions.Bson
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.JobPersistenceException
import org.quartz.TriggerKey
import org.quartz.spi.OperableTrigger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Date

class LocksDao(
    private val locksCollection: MongoCollection<Document>,
    private val clock: Clock,
    private val instanceId: String
) {

    fun createIndex(clustered: Boolean) {
        locksCollection.createIndex(
            Projections.include(KEY_GROUP, KEY_NAME, LOCK_TYPE),
            IndexOptions().unique(true)
        )
        if (!clustered) {
            // Need this to stop table scan when removing all locks
            locksCollection.createIndex(Projections.include(LOCK_INSTANCE_ID))
            // remove all locks for this instance on startup
            locksCollection.deleteMany(Filters.eq(LOCK_INSTANCE_ID, instanceId))
        }
    }

    fun findJobLock(job: JobKey): Document? {
        val filter = createJobLockFilter(job)
        return locksCollection.find(filter).first()
    }

    fun findTriggerLock(trigger: TriggerKey): Document? {
        val filter = createTriggerLockFilter(trigger)
        return locksCollection.find(filter).first()
    }

    fun findOwnTriggersLocks(): MutableList<TriggerKey> {
        val filter = createTriggersLocksFilter(instanceId)
        return locksCollection.find(filter).map { toTriggerKey(it) }.toMutableList()
    }

    fun lockJob(job: JobDetail) {
        log.debug("Inserting lock for job {}", job.key)
        val lock = createJobLock(job.key, instanceId, clock.now())
        insertLock(lock)
    }

    fun lockTrigger(key: TriggerKey) {
        log.info("Inserting lock for trigger {}", key)
        val lock = createTriggerLock(key, instanceId, clock.now())
        insertLock(lock)
    }

    /**
     * Lock given trigger iff its **lockTime** haven't changed.
     *
     *
     * Update is performed using "Update document if current" pattern
     * to update iff document in DB hasn't changed - haven't been relocked
     * by other scheduler.
     *
     * @param key         identifies trigger lock
     * @param lockTime    expected current lockTime
     * @return false when not found or caught an exception
     */
    fun relock(key: TriggerKey, lockTime: Date): Boolean {
        val updateResult = try {
            locksCollection.updateOne(
                createRelockFilter(key, lockTime),
                createLockUpdateDocument(instanceId, clock.now())
            )
        } catch (e: MongoException) {
            log.error("Relock failed because: " + e.message, e)
            return false
        }
        if (updateResult.modifiedCount == 1L) {
            log.info("Scheduler {} relocked the trigger: {}", instanceId, key)
            return true
        }
        log.info("Scheduler {} couldn't relock the trigger {} with lock time: {}", instanceId, key, lockTime.time)
        return false
    }

    /**
     * Reset lock time on own lock.
     *
     * @throws JobPersistenceException in case of errors from Mongo
     * @param key    trigger whose lock to refresh
     * @return true on successful update
     */
    @Throws(JobPersistenceException::class)
    fun updateOwnLock(key: TriggerKey): Boolean {
        val updateResult: UpdateResult
        updateResult = try {
            locksCollection.updateMany(
                toFilter(key, instanceId),
                createLockUpdateDocument(instanceId, clock.now())
            )
        } catch (e: MongoException) {
            log.error("Lock refresh failed because: " + e.message, e)
            throw JobPersistenceException("Lock refresh for scheduler: $instanceId", e)
        }
        if (updateResult.modifiedCount == 1L) {
            log.info("Scheduler {} refreshed locking time.", instanceId)
            return true
        }
        log.info("Scheduler {} couldn't refresh locking time", instanceId)
        return false
    }

    fun remove(lock: Document) {
        locksCollection.deleteMany(lock)
    }

    /**
     * Unlock the trigger if it still belongs to the current scheduler.
     *
     * @param trigger    to unlock
     */
    fun unlockTrigger(trigger: OperableTrigger) {
        log.debug("Removing trigger lock {}.{}", trigger.key, instanceId)
        remove(toFilter(trigger.key, instanceId))
        log.debug("Trigger lock {}.{} removed.", trigger.key, instanceId)
    }

    fun unlockJob(job: JobDetail) {
        log.debug("Removing lock for job {}", job.key)
        remove(createJobLockFilter(job.key))
    }

    private fun insertLock(lock: Document) {
        locksCollection.insertOne(lock)
    }

    private fun remove(filter: Bson) {
        locksCollection.deleteMany(filter)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(LocksDao::class.java)
    }

}
