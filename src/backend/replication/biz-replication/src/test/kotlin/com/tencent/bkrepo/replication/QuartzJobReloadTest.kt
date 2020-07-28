package com.tencent.bkrepo.replication

import com.tencent.bkrepo.replication.constant.DEFAULT_GROUP_ID
import com.tencent.bkrepo.replication.service.ScheduleService
import org.junit.jupiter.api.Test
import org.quartz.InterruptableJob
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.Scheduler
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory
import org.slf4j.LoggerFactory
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

internal class QuartzJobReloadTest {

    @Test
    fun test() {
        thread {
            // 添加任务
            sleep(5)
            taskMap["1"] = "new"
            sleep(5)
            taskMap["2"] = "new"
            taskMap.remove("1")
            sleep(5)
            taskMap["3"] = "new"
            sleep(5)
            taskMap["4"] = "new"
        }

        repeat(1) {
            thread {
                val scheduler = createScheduler("scheduler$it")
                val scheduleService = ScheduleService(scheduler)
                // 加载任务
                while (true) {
                    logger.info("Start to reload task")
                    val taskIdList = listUndoFullTask()
                    val jobKeyList = scheduleService.listJobKeys().map { it.name }
                    val expiredTaskId = jobKeyList subtract taskIdList

                    var newTaskCount = 0
                    var expiredTaskCount = 0
                    val totalCount = taskIdList.size

                    // 移除过期job
                    expiredTaskId.forEach {
                        scheduleService.deleteJob(it)
                        expiredTaskCount += 1
                    }

                    // 创建新job
                    taskIdList.forEach { id ->
                        if (!scheduleService.checkExists(id)) {
                            val jobDetail = createJobDetail(id)
                            val trigger = createTrigger(id)
                            scheduleService.scheduleJob(jobDetail, trigger)
                            newTaskCount += 1
                        }
                    }
                    logger.info("Success to reload replication task, total: $totalCount, new: $newTaskCount, expired: $expiredTaskCount")
                    sleep(3)
                }
            }
        }

        sleep(60)
    }

    private fun createJobDetail(id: String): JobDetail {
        return JobBuilder.newJob(HelloJob::class.java)
            .withIdentity(id, DEFAULT_GROUP_ID)
            .usingJobData("id", id)
            .requestRecovery()
            .build()
    }

    private fun createTrigger(id: String): Trigger {
        return TriggerBuilder.newTrigger()
            .withIdentity(id, DEFAULT_GROUP_ID)
            .startNow()
            .build()
    }

    private fun listUndoFullTask(): List<String> {
        return taskMap.filter {
            it.value != "finished"
        }.keys.toList()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(QuartzJobReloadTest::class.java)
        private val taskMap = ConcurrentHashMap<String, String>()
        private fun sleep(seconds: Int) {
            Thread.sleep(seconds * 1000L)
        }
        private fun createScheduler(name: String): Scheduler {
            val stdSchedulerFactory = StdSchedulerFactory()
            val props = Properties()
            props["org.quartz.scheduler.instanceName"] = name
            props["org.quartz.threadPool.threadCount"] = "10"
            stdSchedulerFactory.initialize(props)
            return stdSchedulerFactory.scheduler.apply { start() }
        }
    }

    class HelloJob: InterruptableJob {

        private lateinit var currentThread: Thread

        override fun interrupt() {
            println("interrupt")
            currentThread.interrupt()
        }

        override fun execute(context: JobExecutionContext) {
            currentThread = Thread.currentThread()
            val id = context.jobDetail.jobDataMap.getString("id")
            logger.info("job[$id] start")
            taskMap[id] = "finished"
            logger.info("job[$id] end")
        }
    }
}