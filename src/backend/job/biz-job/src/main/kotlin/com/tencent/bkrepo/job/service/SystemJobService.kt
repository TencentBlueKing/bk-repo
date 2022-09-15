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
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.support.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.TimeUnit
import javax.annotation.Resource


@Service
class SystemJobService(val jobs: List<BatchJob<*>>) {

    @Resource
    private val ctx: ApplicationContext? = null

    fun detail() :List<JobDetail>{
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
                    running = it.isRunning(),
                    lastBeginTime = it.lastBeginTime,
                    lastEndTime = it.lastEndTime,
                    lastExecuteTime = it.lastExecuteTime,
                    nextExecuteTime = getNextExecuteTime(it.batchJobProperties,
                            it.lastBeginTime,
                            it.lastEndTime
                    )
                )
                jobDetails.add(jobDetail)
            }
        }
        return jobDetails
    }

    private fun getNextExecuteTime(batchJobProperties: BatchJobProperties,
                           lastBeginTime: LocalDateTime?,
                           lastEndTime: LocalDateTime?
    ): LocalDateTime? {
        // 没启用，没下次执行执行时间
        if (!batchJobProperties.enabled) {
            return null
        }
        var finalNextTime: Date
        // 不根据cron表达式
        if (batchJobProperties.cron.equals("-")) {
            return if (lastBeginTime != null) {
                // 根据trigger和上次调用取下次
                var lastFinshTime = Date.from(lastEndTime!!.atZone(ZoneId.systemDefault()).toInstant())
                var lastStartTime = Date.from(lastBeginTime!!.atZone(ZoneId.systemDefault()).toInstant())
                finalNextTime = if (batchJobProperties.fixedDelay != 0L) {
                    val periodicTrigger = PeriodicTrigger(batchJobProperties.fixedDelay, TimeUnit.MILLISECONDS)
                    periodicTrigger.setInitialDelay(batchJobProperties.initialDelay)
                    periodicTrigger.setFixedRate(false)
                    periodicTrigger.nextExecutionTime(SimpleTriggerContext(lastFinshTime,lastFinshTime,lastStartTime))
                } else {
                    val periodicTrigger = PeriodicTrigger(batchJobProperties.fixedRate, TimeUnit.MILLISECONDS)
                    periodicTrigger.setInitialDelay(batchJobProperties.initialDelay)
                    periodicTrigger.setFixedRate(true)
                    periodicTrigger.nextExecutionTime(SimpleTriggerContext(lastFinshTime,lastFinshTime,lastStartTime))
                }
                LocalDateTime.ofInstant(finalNextTime.toInstant(), ZoneId.systemDefault())
            } else {
                // 获取容器启动时间，增加initaldelay
                finalNextTime = if (ctx != null) {
                    Date(ctx.startupDate + batchJobProperties.initialDelay)
                } else {
                    Date(System.currentTimeMillis() + batchJobProperties.initialDelay)
                }
                LocalDateTime.ofInstant(finalNextTime.toInstant(), ZoneId.systemDefault())
            }
        }
        // 根据cron表达式
        val cronTrigger = CronTrigger(batchJobProperties.cron)
        finalNextTime = if (lastBeginTime != null) {
            var lastFinshTime = Date.from(lastEndTime!!.atZone(ZoneId.systemDefault()).toInstant())
            var lastStartTime = Date.from(lastBeginTime!!.atZone(ZoneId.systemDefault()).toInstant())
            cronTrigger.nextExecutionTime(SimpleTriggerContext(lastFinshTime,lastFinshTime,lastStartTime))
        } else {
            cronTrigger.nextExecutionTime(SimpleTriggerContext(null,null,null))
        }
        return LocalDateTime.ofInstant(finalNextTime.toInstant(), ZoneId.systemDefault())
    }

    fun update(name: String, status: Boolean): Boolean{
        if (status) {
            jobs.filter { batchJob -> batchJob.getJobName().equals(name) }.get(0).start()
        } else {
            jobs.filter { batchJob -> batchJob.getJobName().equals(name) }.get(0).stop()
        }
        return true
    }
}
