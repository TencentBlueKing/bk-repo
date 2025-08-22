/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.schedule

import org.slf4j.LoggerFactory
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder
import org.springframework.scheduling.config.CronTask
import org.springframework.scheduling.config.FixedDelayTask
import org.springframework.scheduling.config.FixedRateTask
import org.springframework.scheduling.config.ScheduledTask
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * Spring调度任务注册
 * */
class SpringScheduleJobRegistrar(
    private val builder: ThreadPoolTaskSchedulerBuilder,
) : JobRegistrar {
    private var initial = false
    lateinit var taskRegistrar: ScheduledTaskRegistrar

    private val taskMap = mutableMapOf<String, SpringTask>()

    override fun init() {
        if (!initial) {
            val taskScheduler = builder.build()
            taskScheduler.initialize()
            taskRegistrar.setTaskScheduler(taskScheduler)
            initial = true
        }
    }

    override fun register(job: Job) {
        val scheduleConf = job.scheduleConf
        val task = when (job.scheduleType) {
            JobScheduleType.CRON -> {
                addCronTask(job.runnable, scheduleConf)
            }

            JobScheduleType.FIX_DELAY -> {
                addFixedDelayTask(job.runnable, scheduleConf.toLong(), scheduleConf.toLong())
            }

            JobScheduleType.FIX_RATE -> {
                addFixedRateTask(job.runnable, scheduleConf.toLong(), scheduleConf.toLong())
            }
        } ?: return
        val name = job.name
        val springTask = SpringTask(job, task)
        val old = taskMap.putIfAbsent(name, springTask)
        if (old != null) {
            unregister(springTask.job)
        }
        logger.info("Registering job [$name]")
    }

    override fun unregister(job: Job) {
        val name = job.name
        val springJob = taskMap.remove(name)
        springJob?.let {
            it.task.cancel(false)
            logger.info("Unregistering job [$name]")
        }
    }

    override fun update(job: Job) {
        unregister(job)
        register(job)
    }

    override fun list(): List<Job> {
        return taskMap.values.map { it.job }
    }

    override fun unload() {
        taskMap.map { it.value }.forEach {
            unregister(it.job)
        }
        logger.info("${this.javaClass.simpleName} unloaded")
    }

    data class SpringTask(
        val job: Job,
        val task: ScheduledTask,
    )

    private fun addCronTask(runnable: Runnable, cron: String): ScheduledTask? {
        val cronTask = CronTask(runnable, cron)
        return taskRegistrar.scheduleCronTask(cronTask)
    }

    private fun addFixedRateTask(runnable: Runnable, interval: Long, initialDelay: Long): ScheduledTask? {
        val fixedRateTask = FixedRateTask(runnable, interval, initialDelay)
        return taskRegistrar.scheduleFixedRateTask(fixedRateTask)
    }

    private fun addFixedDelayTask(runnable: Runnable, delay: Long, initialDelay: Long): ScheduledTask? {
        val task = FixedDelayTask(runnable, delay, initialDelay)
        return taskRegistrar.scheduleFixedDelayTask(task)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SpringScheduleJobRegistrar::class.java)
    }
}
