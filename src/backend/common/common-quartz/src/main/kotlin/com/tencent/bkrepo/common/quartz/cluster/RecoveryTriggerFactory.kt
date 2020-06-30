package com.tencent.bkrepo.common.quartz.cluster

import org.quartz.JobDataMap
import org.quartz.Scheduler
import org.quartz.SimpleTrigger
import org.quartz.impl.triggers.SimpleTriggerImpl
import org.quartz.spi.OperableTrigger
import java.util.Date

class RecoveryTriggerFactory(private val instanceId: String) {
    fun from(trigger: OperableTrigger): OperableTrigger {
        val tKey = trigger.key
        val jKey = trigger.jobKey
        //TODO was ftRec.getScheduleTimestamp();
        val scheduleTimestamp = System.currentTimeMillis()
        //TODO was ftRec.getFireTimestamp()
        val fireTimestamp = System.currentTimeMillis()
        val rcvryTrig = SimpleTriggerImpl()
        rcvryTrig.name = "recover_" + instanceId + "_" + System.currentTimeMillis() //String.valueOf(recoverIds++)
        rcvryTrig.group = Scheduler.DEFAULT_RECOVERY_GROUP
        rcvryTrig.startTime = Date(scheduleTimestamp)
        rcvryTrig.jobName = jKey.name
        rcvryTrig.jobGroup = jKey.group
        rcvryTrig.misfireInstruction = SimpleTrigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY
        //TODO was ftRec.getPriority()
        rcvryTrig.priority = trigger.priority

        // Cannot reuse JobDataMap, because the original trigger
        // is may be persisted after applying misfire.
        val jd = JobDataMap(trigger.jobDataMap)
        jd[Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_NAME] = tKey.name
        jd[Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_GROUP] = tKey.group
        jd[Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_FIRETIME_IN_MILLISECONDS] = fireTimestamp.toString()
        //TODO jd.put(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS,
        //TODO String.valueOf(scheduleTimestamp));
        rcvryTrig.jobDataMap = jd
        rcvryTrig.computeFirstFireTime(null)
        return rcvryTrig
    }
}
