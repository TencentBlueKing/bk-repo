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

package com.tencent.bkrepo.job.service

import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.bkrepo.job.config.properties.BatchJobProperties
import com.tencent.bkrepo.job.pojo.JobDetail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronTrigger
import org.springframework.scheduling.support.PeriodicTrigger
import org.springframework.scheduling.support.SimpleTriggerContext
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

import java.util.concurrent.TimeUnit

@Service
class SystemJobService(val jobs: List<BatchJob<*>>) {

    @Autowired
    private val ctx: ApplicationContext? = null

    fun detail(): List<JobDetail> {
        val jobDetails = mutableListOf<JobDetail>()
        jobs.forEach {
            with(it.batchJobProperties) {
                val jobDetail = JobDetail(
                    name = it.getJobName(),
                    enabled = enabled,
                    cron = cron,
                    fixedDelay = fixedDelay,
                    fixedRate = fixedRate,
                    initialDelay = initialDelay,
                    running = it.inProcess(),
                    lastBeginTime = it.lastBeginTime,
                    lastEndTime = it.lastEndTime,
                    lastExecuteTime = it.lastExecuteTime,
                    nextExecuteTime = getNextExecuteTime(
                        it.batchJobProperties,
                        it.lastBeginTime,
                        it.lastEndTime,
                    ),
                )
                jobDetails.add(jobDetail)
            }
        }
        return jobDetails
    }

    private fun getNextExecuteTime(
        batchJobProperties: BatchJobProperties,
        lastBeginTime: LocalDateTime?,
        lastEndTime: LocalDateTime?,
    ): LocalDateTime? {
        if (!batchJobProperties.enabled) {
            return null
        }
        val finalNextTime: Date
        val triggerContext = buildTriggerContext(lastBeginTime, lastEndTime)
        if (batchJobProperties.cron.equals(ScheduledTaskRegistrar.CRON_DISABLED)) {
            finalNextTime = getNextTimeByPeriodic(
                batchJobProperties,
                triggerContext,
                lastBeginTime,
            )
        } else {
            finalNextTime = getNextTimeByCron(batchJobProperties, triggerContext)
        }
        return LocalDateTime.ofInstant(finalNextTime.toInstant(), ZoneId.systemDefault())
    }

    /**
     * 构建triggerContext
     */
    private fun buildTriggerContext(
        lastBeginTime: LocalDateTime?,
        lastEndTime: LocalDateTime?,
    ): SimpleTriggerContext {
        val lastFinshTime = if (lastEndTime != null) {
            Date.from(lastEndTime.atZone(ZoneId.systemDefault()).toInstant())
        } else {
            null
        }
        val lastStartTime = if (lastBeginTime != null) {
            Date.from(lastBeginTime.atZone(ZoneId.systemDefault()).toInstant())
        } else {
            null
        }
        return SimpleTriggerContext(lastStartTime, lastStartTime, lastFinshTime)
    }

    /**
     * 根据Cron表达式来获取下次任务时间
     */
    private fun getNextTimeByCron(
        batchJobProperties: BatchJobProperties,
        triggerContext: SimpleTriggerContext,
    ): Date {
        val cronTrigger = CronTrigger(batchJobProperties.cron)
        return cronTrigger.nextExecutionTime(triggerContext)
    }

    /**
     * 根据Periodic获取下次任务时间
     */
    private fun getNextTimeByPeriodic(
        batchJobProperties: BatchJobProperties,
        triggerContext: SimpleTriggerContext,
        lastBeginTime: LocalDateTime?,
    ): Date {
        val fixRateStatus = if (batchJobProperties.fixedDelay != 0L) false else true
        val period = if (batchJobProperties.fixedDelay != 0L) {
            batchJobProperties.fixedDelay
        } else {
            batchJobProperties.fixedRate
        }
        val periodicTrigger = PeriodicTrigger(period, TimeUnit.MILLISECONDS)
        periodicTrigger.setInitialDelay(batchJobProperties.initialDelay)
        periodicTrigger.setFixedRate(fixRateStatus)
        return if (lastBeginTime == null && ctx != null) {
            Date(ctx.startupDate + batchJobProperties.initialDelay)
        } else {
            periodicTrigger.nextExecutionTime(triggerContext)
        }
    }

    fun update(name: String, enabled: Boolean, running: Boolean): Boolean {
        if (enabled) {
            jobs.filter { batchJob -> batchJob.getJobName().equals(name) }.first().enable()
        } else {
            jobs.filter { batchJob -> batchJob.getJobName().equals(name) }.first().disable()
        }
        if (running) {
            jobs.filter { batchJob -> batchJob.getJobName().equals(name) }.first().start()
        } else {
            jobs.filter { batchJob -> batchJob.getJobName().equals(name) }.first().stop()
        }
        return true
    }

    fun stop(name: String?, maxWaitTime: Long, failover: Boolean = false) {
        if (name == null) {
            jobs.forEach { it.stop(maxWaitTime, failover) }
        } else {
            jobs.first { it.getJobName() == name }.stop(maxWaitTime, failover)
        }
    }
}
