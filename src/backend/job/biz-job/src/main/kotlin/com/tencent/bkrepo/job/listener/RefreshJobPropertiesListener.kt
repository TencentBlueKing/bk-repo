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

package com.tencent.bkrepo.job.listener

import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.job.batch.base.BatchJob
import com.tencent.bkrepo.job.schedule.JobRegistrar
import com.tencent.bkrepo.job.schedule.JobUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.event.SmartApplicationListener
import org.springframework.stereotype.Component

@Component
class RefreshJobPropertiesListener(private val jobRegistrar: JobRegistrar) : SmartApplicationListener {
    private var refreshKeys = mutableListOf<String>()
    private val pattern = "-[a-z]".toRegex()
    override fun onApplicationEvent(event: ApplicationEvent) {
        if (event is EnvironmentChangeEvent) {
            val keys = event.keys.map { JOB_PROP_KEY_REGEX.find(it)?.value.orEmpty() }
            refreshKeys.addAll(keys)
            return
        }
        if (event is RefreshScopeRefreshedEvent && refreshKeys.isNotEmpty()) {
            refreshKeys.forEach { key ->
                try {
                    val jobName = key.substring(4, key.indexOf(".", 4))
                        .replace(pattern) { it.value.last().uppercase() } + JOB_SUFFIX
                    val jobBean = SpringContextUtils.getBean(BatchJob::class.java, jobName)
                    logger.info("Job [$jobName] config updated")
                    val job = JobUtils.parseBatchJob(jobBean)
                    jobRegistrar.update(job)
                } catch (_: NoSuchBeanDefinitionException) {
                    // ignore
                } catch (e: Exception) {
                    logger.error("Refresh key [$key] failed.")
                }
            }
            refreshKeys.clear()
            return
        }
    }

    override fun supportsEventType(eventType: Class<out ApplicationEvent>): Boolean {
        return RefreshScopeRefreshedEvent::class.java.isAssignableFrom(eventType) ||
            EnvironmentChangeEvent::class.java.isAssignableFrom(eventType)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RefreshJobPropertiesListener::class.java)
        private const val JOB_SUFFIX = "Job"
        private val JOB_PROP_KEY_REGEX = Regex("job.[\\w-]+.(cron$|fixedDelay$|fixedRate$)")
    }
}
