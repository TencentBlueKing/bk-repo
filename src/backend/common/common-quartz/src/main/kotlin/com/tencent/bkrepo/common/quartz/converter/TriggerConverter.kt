package com.tencent.bkrepo.common.quartz.converter

import com.tencent.bkrepo.common.quartz.STATE_WAITING
import com.tencent.bkrepo.common.quartz.TRIGGER_JOB_ID
import com.tencent.bkrepo.common.quartz.TRIGGER_NEXT_FIRE_TIME
import com.tencent.bkrepo.common.quartz.TRIGGER_STATE
import com.tencent.bkrepo.common.quartz.dao.JobDao
import com.tencent.bkrepo.common.quartz.util.Keys.KEY_GROUP
import com.tencent.bkrepo.common.quartz.util.Keys.KEY_NAME
import org.bson.Document
import org.bson.types.ObjectId
import org.quartz.Job
import org.quartz.JobKey
import org.quartz.JobPersistenceException
import org.quartz.TriggerKey
import org.quartz.spi.OperableTrigger
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TriggerConverter(
    private val jobDao: JobDao,
    private val jobDataConverter: JobDataConverter
) {

    /**
     * Converts trigger into document.
     * Depending on the config, job data map can be stored
     * as a `base64` encoded (default) or plain object.
     */
    @Throws(JobPersistenceException::class)
    fun toDocument(newTrigger: OperableTrigger, jobId: ObjectId): Document {
        val trigger = convertToBson(newTrigger, jobId)
        jobDataConverter.toDocument(newTrigger.jobDataMap, trigger)
        val triggerPropertiesConverter = TriggerPropertiesConverter.getConverterFor(newTrigger)
        return triggerPropertiesConverter.injectExtraPropertiesForInsert(newTrigger, trigger)
    }

    /**
     * Restore trigger from Mongo Document.
     *
     * @param triggerKey [TriggerKey] instance.
     * @param triggerDoc mongo [Document] to read from.
     * @return trigger from Document or null when trigger has no associated job
     * @throws JobPersistenceException if could not construct trigger instance
     * or could not deserialize job data map.
     */
    @Throws(JobPersistenceException::class)
    fun toTrigger(triggerKey: TriggerKey, triggerDoc: Document): OperableTrigger? {
        val trigger = toTriggerWithOptionalJob(triggerKey, triggerDoc)
        return trigger.jobKey?.let { trigger }
    }

    /**
     * Restore trigger from Mongo Document.
     *
     * @param triggerKey [TriggerKey] instance.
     * @param triggerDoc mongo [Document] to read from.
     * @return trigger from Document even if no associated job exists
     * @throws JobPersistenceException if could not construct trigger instance
     * or could not deserialize job data map.
     */
    @Throws(JobPersistenceException::class)
    fun toTriggerWithOptionalJob(triggerKey: TriggerKey, triggerDoc: Document): OperableTrigger {
        val trigger = createNewInstance(triggerDoc)
        val propertiesConverter = TriggerPropertiesConverter.getConverterFor(trigger)
        loadCommonProperties(triggerKey, triggerDoc, trigger)
        jobDataConverter.toJobData(triggerDoc, trigger.jobDataMap)
        loadStartAndEndTimes(triggerDoc, trigger)
        propertiesConverter.setExtraPropertiesAfterInstantiation(trigger, triggerDoc)
        val jobId = triggerDoc[TRIGGER_JOB_ID]!!
        jobDao.getById(jobId)?.let { trigger.jobKey = JobKey(it.getString(KEY_NAME), it.getString(KEY_GROUP)) }
        return trigger
    }

    @Throws(JobPersistenceException::class)
    fun toTrigger(doc: Document): OperableTrigger? {
        val key = TriggerKey(doc.getString(KEY_NAME), doc.getString(KEY_GROUP))
        return toTrigger(key, doc)
    }

    @Throws(JobPersistenceException::class)
    fun toTriggerWithOptionalJob(doc: Document): OperableTrigger {
        val key = TriggerKey(doc.getString(KEY_NAME), doc.getString(KEY_GROUP))
        return toTriggerWithOptionalJob(key, doc)
    }

    private fun convertToBson(newTrigger: OperableTrigger, jobId: ObjectId): Document {
        val trigger = Document()
        trigger[TRIGGER_STATE] = STATE_WAITING
        trigger[TRIGGER_CALENDAR_NAME] = newTrigger.calendarName
        trigger[TRIGGER_CLASS] = newTrigger.javaClass.name
        trigger[TRIGGER_DESCRIPTION] = newTrigger.description
        trigger[TRIGGER_END_TIME] = newTrigger.endTime
        trigger[TRIGGER_FINAL_FIRE_TIME] = newTrigger.finalFireTime
        trigger[TRIGGER_FIRE_INSTANCE_ID] = newTrigger.fireInstanceId
        trigger[TRIGGER_JOB_ID] = jobId
        trigger[KEY_NAME] = newTrigger.key.name
        trigger[KEY_GROUP] = newTrigger.key.group
        trigger[TRIGGER_MISFIRE_INSTRUCTION] = newTrigger.misfireInstruction
        trigger[TRIGGER_NEXT_FIRE_TIME] = newTrigger.nextFireTime
        trigger[TRIGGER_PREVIOUS_FIRE_TIME] = newTrigger.previousFireTime
        trigger[TRIGGER_PRIORITY] = newTrigger.priority
        trigger[TRIGGER_START_TIME] = newTrigger.startTime
        return trigger
    }

    @Throws(JobPersistenceException::class)
    private fun createNewInstance(triggerDoc: Document): OperableTrigger {
        val triggerClassName = triggerDoc.getString(TRIGGER_CLASS)
        return try {
            val triggerClass = getTriggerClassLoader().loadClass(triggerClassName) as Class<OperableTrigger>
            triggerClass.newInstance()
        } catch (e: ClassNotFoundException) {
            throw JobPersistenceException("Could not find trigger class $triggerClassName")
        } catch (e: Exception) {
            throw JobPersistenceException("Could not instantiate trigger class $triggerClassName")
        }
    }

    private fun getTriggerClassLoader(): ClassLoader {
        return Job::class.java.classLoader
    }

    private fun loadCommonProperties(triggerKey: TriggerKey, triggerDoc: Document, trigger: OperableTrigger) {
        trigger.key = triggerKey
        trigger.calendarName = triggerDoc.getString(TRIGGER_CALENDAR_NAME)
        trigger.description = triggerDoc.getString(TRIGGER_DESCRIPTION)
        trigger.fireInstanceId = triggerDoc.getString(TRIGGER_FIRE_INSTANCE_ID)
        trigger.misfireInstruction = triggerDoc.getInteger(TRIGGER_MISFIRE_INSTRUCTION)
        trigger.nextFireTime = triggerDoc.getDate(TRIGGER_NEXT_FIRE_TIME)
        trigger.previousFireTime = triggerDoc.getDate(TRIGGER_PREVIOUS_FIRE_TIME)
        trigger.priority = triggerDoc.getInteger(TRIGGER_PRIORITY)
    }

    private fun loadStartAndEndTimes(triggerDoc: Document, trigger: OperableTrigger) {
        loadStartAndEndTime(triggerDoc, trigger)
    }

    private fun loadStartAndEndTime(triggerDoc: Document, trigger: OperableTrigger) {
        try {
            trigger.startTime = triggerDoc.getDate(TRIGGER_START_TIME)
            trigger.endTime = triggerDoc.getDate(TRIGGER_END_TIME)
        } catch (e: IllegalArgumentException) {
            //Ignore illegal arg exceptions thrown by triggers doing JIT validation of start and endtime
            log.warn("Trigger had illegal start / end time combination: {}", trigger.key, e)
        }
    }

    companion object {
        private const val TRIGGER_CALENDAR_NAME = "calendarName"
        private const val TRIGGER_CLASS = "class"
        private const val TRIGGER_DESCRIPTION = "description"
        private const val TRIGGER_END_TIME = "endTime"
        private const val TRIGGER_FINAL_FIRE_TIME = "finalFireTime"
        private const val TRIGGER_FIRE_INSTANCE_ID = "fireInstanceId"
        private const val TRIGGER_MISFIRE_INSTRUCTION = "misfireInstruction"
        private const val TRIGGER_PREVIOUS_FIRE_TIME = "previousFireTime"
        private const val TRIGGER_PRIORITY = "priority"
        private const val TRIGGER_START_TIME = "startTime"
        private val log: Logger = LoggerFactory.getLogger(TriggerConverter::class.java)
    }
}
