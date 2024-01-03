/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.config

import com.tencent.bkrepo.common.service.shutdown.ServiceShutdownHook
import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.bkrepo.job.config.properties.BatchJobProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.task.TaskSchedulerBuilder
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.CronTask
import org.springframework.scheduling.config.FixedDelayTask
import org.springframework.scheduling.config.FixedRateTask
import org.springframework.scheduling.config.ScheduledTask
import org.springframework.scheduling.config.ScheduledTaskRegistrar

@Configuration
class ScheduledTaskConfigurer(
    val jobs: List<BatchJob<*>>,
    val builder: TaskSchedulerBuilder,
) : SchedulingConfigurer {
    init {
        scheduledTaskConfigurer = this
    }

    private var initial = false

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        if (!initial) {
            Companion.taskRegistrar = taskRegistrar
            val taskScheduler = builder.build()
            taskScheduler.initialize()
            Companion.taskRegistrar.setTaskScheduler(taskScheduler)
            ServiceShutdownHook.add { jobs.forEach { it.stop(it.batchJobProperties.stopTimeout, true) } }
        }
        jobs.filter { it.batchJobProperties.enabled }.forEach {
            val properties = it.batchJobProperties
            val add = addTask(properties, it::start)
            if (add) {
                logger.info("Add scheduled task [${it.getJobName()}]")
            }
        }
    }

    private fun addTask(properties: BatchJobProperties, runnable: Runnable): Boolean {
        with(properties) {
            if (cron.isNotEmpty() && Scheduled.CRON_DISABLED != cron) {
                addCronTask(runnable, cron)
                return true
            }
            if (fixedRate > 0) {
                addFixedRateTask(runnable, fixedRate, initialDelay)
                return true
            }
            if (fixedDelay > 0) {
                addFixedDelayTask(runnable, fixedDelay, initialDelay)
                return true
            }
            return false
        }
    }

    private fun addCronTask(runnable: Runnable, cron: String) {
        val cronTask = CronTask(runnable, cron)
        addScheduledTask(taskRegistrar.scheduleCronTask(cronTask))
    }

    private fun addFixedRateTask(runnable: Runnable, interval: Long, initialDelay: Long) {
        val fixedRateTask = FixedRateTask(runnable, interval, initialDelay)
        addScheduledTask(taskRegistrar.scheduleFixedRateTask(fixedRateTask))
    }

    private fun addFixedDelayTask(runnable: Runnable, delay: Long, initialDelay: Long) {
        val task = FixedDelayTask(runnable, delay, initialDelay)
        addScheduledTask(taskRegistrar.scheduleFixedDelayTask(task))
    }

    private fun addScheduledTask(scheduledTask: ScheduledTask?) {
        scheduledTask?.let {
            scheduledTaskSet.add(scheduledTask)
        }
    }

    companion object {
        private lateinit var taskRegistrar: ScheduledTaskRegistrar
        private lateinit var scheduledTaskConfigurer: ScheduledTaskConfigurer
        val scheduledTaskSet = mutableSetOf<ScheduledTask>()
        private val logger = LoggerFactory.getLogger(ScheduledTaskConfigurer::class.java)

        fun reloadScheduledTask() {
            scheduledTaskSet.forEach { it.cancel() }
            scheduledTaskSet.clear()
            scheduledTaskConfigurer.configureTasks(taskRegistrar)
            logger.info("Reload scheduled task")
        }
    }
}
