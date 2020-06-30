package com.tencent.bkrepo.common.quartz.util

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.tencent.bkrepo.common.quartz.LOCK_INSTANCE_ID
import com.tencent.bkrepo.common.quartz.LOCK_TIME
import org.bson.Document
import org.bson.conversions.Bson
import org.quartz.JobKey
import org.quartz.TriggerKey
import org.quartz.utils.Key
import java.util.Date

object Keys {

    const val LOCK_TYPE = "type"
    const val KEY_NAME = "keyName"
    const val KEY_GROUP = "keyGroup"
    val KEY_AND_GROUP_FIELDS = Projections.include(KEY_GROUP, KEY_NAME)

    fun createJobLockFilter(key: JobKey): Bson {
        return createLockFilter(LockType.J, key)
    }

    fun createTriggerLockFilter(triggerKey: TriggerKey): Bson {
        return createLockFilter(LockType.T, triggerKey)
    }

    fun createTriggersLocksFilter(instanceId: String?): Bson {
        return Filters.and(
            Filters.eq(LOCK_INSTANCE_ID, instanceId),
            Filters.eq(LOCK_TYPE, LockType.T.name)
        )
    }

    fun createLockRefreshFilter(instanceId: String?): Bson {
        return Filters.eq(LOCK_INSTANCE_ID, instanceId)
    }

    fun createRelockFilter(key: TriggerKey, lockTime: Date): Bson {
        return Filters.and(
            createTriggerLockFilter(key),
            createLockTimeFilter(lockTime)
        )
    }

    fun createJobLock(jobKey: JobKey, instanceId: String, lockTime: Date): Document {
        return createLock(LockType.J, instanceId, jobKey, lockTime)
    }

    fun createTriggerLock(triggerKey: TriggerKey, instanceId: String, lockTime: Date): Document {
        return createLock(LockType.T, instanceId, triggerKey, lockTime)
    }

    fun toFilter(key: Key<*>): Bson {
        return Filters.and(
            Filters.eq(KEY_GROUP, key.group),
            Filters.eq(KEY_NAME, key.name)
        )
    }

    fun toFilter(key: Key<*>, instanceId: String?): Bson {
        return Filters.and(
            Filters.eq(KEY_GROUP, key.group),
            Filters.eq(KEY_NAME, key.name),
            Filters.eq(LOCK_INSTANCE_ID, instanceId)
        )
    }

    fun toJobKey(dbo: Document): JobKey {
        return JobKey(dbo.getString(KEY_NAME), dbo.getString(KEY_GROUP))
    }

    fun toTriggerKey(dbo: Document): TriggerKey {
        return TriggerKey(dbo.getString(KEY_NAME), dbo.getString(KEY_GROUP))
    }

    private fun createLock(type: LockType, instanceId: String, key: Key<*>, lockTime: Date): Document {
        val lock = Document()
        lock[LOCK_TYPE] = type.name
        lock[KEY_GROUP] = key.group
        lock[KEY_NAME] = key.name
        lock[LOCK_INSTANCE_ID] = instanceId
        lock[LOCK_TIME] = lockTime
        return lock
    }

    fun createLockUpdateDocument(instanceId: String?, newLockTime: Date?): Document {
        return Document(
            "\$set", Document()
                .append(LOCK_INSTANCE_ID, instanceId)
                .append(LOCK_TIME, newLockTime)
        )
    }

    private fun <T> createLockFilter(type: LockType, key: Key<T>): Bson {
        return Filters.and(
            Filters.eq(LOCK_TYPE, type.name),
            Filters.eq(KEY_GROUP, key.group),
            Filters.eq(KEY_NAME, key.name)
        )
    }

    private fun createLockTimeFilter(lockTime: Date): Bson {
        return Filters.eq(LOCK_TIME, lockTime)
    }

    enum class LockType {
        T, J
    }
}
