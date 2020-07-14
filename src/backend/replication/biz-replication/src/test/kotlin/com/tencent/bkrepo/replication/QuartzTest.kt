package com.tencent.bkrepo.replication

import com.tencent.bkrepo.common.api.constant.StringPool.uniqueId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.quartz.CronScheduleBuilder
import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey
import org.quartz.impl.StdSchedulerFactory
import org.quartz.impl.matchers.GroupMatcher
import java.util.Date

internal class QuartzTest {

    @Test
    @DisplayName("SimpleTrigger执行完后，job和trigger会被删除")
    fun testSimpleTrigger() {
        val scheduler = StdSchedulerFactory.getDefaultScheduler()
        scheduler.start()

        val jobKey = JobKey(uniqueId())
        val triggerKey = TriggerKey(uniqueId())

        val jobDetail = JobBuilder.newJob(SayHelloJob::class.java)
            .withIdentity(jobKey)
            .build()

        val startAt = Date(System.currentTimeMillis() + 2 * 1000)
        val trigger = TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .startAt(startAt)
            .build()
        scheduler.scheduleJob(jobDetail, trigger)

        Thread.sleep(10 * 1000)
        println(scheduler.getJobKeys(GroupMatcher.anyGroup()).size)
        println(scheduler.getTriggersOfJob(jobKey).size)

        Thread.sleep(20 * 1000)
        scheduler.shutdown(true)
    }

    @Test
    @DisplayName("CronTrigger执行完后，job和trigger会保留")
    fun testCronTrigger() {
        val scheduler = StdSchedulerFactory.getDefaultScheduler()
        scheduler.start()

        val jobKey = JobKey(uniqueId())
        val triggerKey = TriggerKey(uniqueId())

        val jobDetail = JobBuilder.newJob(SayHelloJob::class.java)
            .withIdentity(jobKey)
            .build()

        val cronExpression = "0/5 * * * * ?"
        val trigger = TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
            .build()
        scheduler.scheduleJob(jobDetail, trigger)

        Thread.sleep(6 * 1000)
        println(scheduler.getJobKeys(GroupMatcher.anyGroup()).size)
        println(scheduler.getTriggersOfJob(jobKey).size)

        scheduler.unscheduleJob(triggerKey)
        Thread.sleep(5 * 1000)
        println(scheduler.getJobKeys(GroupMatcher.anyGroup()).size)
        println(scheduler.getTriggersOfJob(jobKey).size)


        Thread.sleep(10 * 1000)
        scheduler.shutdown(true)
    }

    @Test
    @DisplayName("删除不存在的job，执行成功不会异常")
    fun testDeleteNonExistJob() {
        val scheduler = StdSchedulerFactory.getDefaultScheduler()
        scheduler.start()

        val jobKey = JobKey(uniqueId())
        scheduler.deleteJob(jobKey)

        Thread.sleep(5 * 1000)
        scheduler.shutdown(true)
    }

    class SayHelloJob : Job {
        override fun execute(context: JobExecutionContext) {
            println("Hello: " + Thread.currentThread().name)
        }
    }
}

