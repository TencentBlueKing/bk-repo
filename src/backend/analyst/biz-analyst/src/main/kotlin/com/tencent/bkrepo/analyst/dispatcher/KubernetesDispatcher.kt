/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.dispatcher

import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.dispatcher.dsl.addContainerItem
import com.tencent.bkrepo.analyst.dispatcher.dsl.addImagePullSecretsItemIfNeed
import com.tencent.bkrepo.analyst.dispatcher.dsl.limits
import com.tencent.bkrepo.analyst.dispatcher.dsl.metadata
import com.tencent.bkrepo.analyst.dispatcher.dsl.requests
import com.tencent.bkrepo.analyst.dispatcher.dsl.resources
import com.tencent.bkrepo.analyst.dispatcher.dsl.spec
import com.tencent.bkrepo.analyst.dispatcher.dsl.template
import com.tencent.bkrepo.analyst.dispatcher.dsl.v1Job
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.execution.KubernetesJobExecutionCluster
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.TemporaryScanTokenService
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.redis.RedisOperation
import com.tencent.bkrepo.statemachine.StateMachine
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Duration

class KubernetesDispatcher(
    executionCluster: KubernetesJobExecutionCluster,
    scannerProperties: ScannerProperties,
    scanService: ScanService,
    subtaskStateMachine: StateMachine,
    temporaryScanTokenService: TemporaryScanTokenService,
    executor: ThreadPoolTaskExecutor,
    redisOperation: RedisOperation,
) : SubtaskPushDispatcher<KubernetesJobExecutionCluster>(
    executionCluster,
    scannerProperties,
    redisOperation,
    scanService,
    subtaskStateMachine,
    temporaryScanTokenService,
    executor,
) {

    private val client by lazy { createClient(executionCluster.kubernetesProperties) }
    private val coreV1Api by lazy { CoreV1Api(client) }
    private val batchV1Api by lazy { BatchV1Api(client) }

    override fun dispatch(subtask: SubScanTask): Boolean {
        var result = false
        var retry = true
        var retryTimes = MAX_RETRY_TIMES
        while (retry && retryTimes > 0) {
            retry = false
            retryTimes--
            try {
                result = createJob(subtask)
            } catch (e: ApiException) {
                retry = resolveCreateJobFailed(e, subtask)
                if (retry && retryTimes > 0) {
                    Thread.sleep(Duration.ofSeconds(MAX_RETRY_TIMES - retryTimes + 1L).toMillis())
                }
            }
        }

        return result
    }

    override fun clean(subtask: SubScanTask, subtaskStatus: String): Boolean {
        val shouldClean = executionCluster.cleanJobAfterSuccess && subtaskStatus == SubScanTaskStatus.SUCCESS.name
        if (shouldClean || subtaskStatus == SubScanTaskStatus.STOPPED.name) {
            // 只清理执行成功或手动停止的Job，失败的Job需要保留用于排查问题
            return cleanJob(jobName(subtask))
        }
        return false
    }

    override fun availableCount(): Int {
        val k8sProps = executionCluster.kubernetesProperties
        val quota = coreV1Api.listNamespacedResourceQuota(
            k8sProps.namespace,
            null, null, null,
            null, null, null,
            null, null, null, null
        )
        val status = quota.items.firstOrNull()?.status
        val hard = status?.hard
        val used = status?.used
        if (hard.isNullOrEmpty() || used.isNullOrEmpty()) {
            return Int.MAX_VALUE
        }
        val limitCpu = hard["limits.cpu"]!!.number.toDouble()
        val limitMem = hard["limits.memory"]!!.number.toLong()
        val jobs = batchV1Api.listNamespacedJob(
            k8sProps.namespace,
            null, null, null,
            null, null, null,
            null, null, null, null
        )
        val executingJobCount = jobs.items.filter {
            (it.status?.failed ?: 0) == 0 && (it.status?.succeeded ?: 0) == 0
        }.size
        val requireCpuPerTask = k8sProps.limitCpu
        val requireMemPerTask = k8sProps.limitMem
        val availableCpu = limitCpu - executingJobCount * requireCpuPerTask
        val availableMem = limitMem - executingJobCount * requireMemPerTask

        return if (availableCpu < requireCpuPerTask || availableMem < requireMemPerTask) {
            0
        } else {
            minOf((availableCpu / requireCpuPerTask).toInt(), (availableMem / requireMemPerTask).toInt())
        }
    }

    override fun name(): String {
        return executionCluster.name
    }

    private fun createJob(subtask: SubScanTask): Boolean {
        val scanner = subtask.scanner
        require(scanner is StandardScanner)
        val jobName = jobName(subtask)
        val containerImage = scanner.image
        val cmd = buildCommand(
            cmd = scanner.cmd,
            baseUrl = scannerProperties.baseUrl,
            subtaskId = subtask.taskId,
            token = subtask.token!!,
            heartbeatTimeout = scannerProperties.heartbeatTimeout,
            username = scannerProperties.username,
            password = scannerProperties.password,
        )
        val k8sProps = executionCluster.kubernetesProperties
        val resReq = ResourceRequirements.calculate(scanner, k8sProps)
        val jobActiveDeadlineSeconds = subtask.scanner.maxScanDuration(subtask.packageSize)
        val body = v1Job {
            apiVersion = "batch/v1"
            kind = "Job"
            metadata {
                namespace = k8sProps.namespace
                name = jobName
            }
            spec {
                backoffLimit = 0
                activeDeadlineSeconds = jobActiveDeadlineSeconds
                ttlSecondsAfterFinished = executionCluster.jobTtlSecondsAfterFinished
                template {
                    spec {
                        addContainerItem {
                            name = jobName
                            image = containerImage
                            command = cmd
                            addImagePullSecretsItemIfNeed(scanner, k8sProps)
                            resources {
                                requests(
                                    cpu = resReq.requestCpu,
                                    memory = resReq.requestMem,
                                    ephemeralStorage = resReq.requestStorage
                                )
                                limits(
                                    cpu = resReq.limitCpu,
                                    memory = resReq.limitMem,
                                    ephemeralStorage = resReq.limitStorage
                                )
                            }
                        }
                        restartPolicy = "Never"
                    }
                }
            }
        }
        batchV1Api.createNamespacedJob(k8sProps.namespace, body, null, null, null)
        logger.info("dispatch subtask[${subtask.taskId}] success")
        return true
    }

    /**
     * 处理接口请求错误
     *
     * @param e 请求错误
     * @param subtask 待执行任务
     *
     * @return 是否处理成功
     */
    private fun resolveCreateJobFailed(e: ApiException, subtask: SubScanTask): Boolean {
        // 处理job名称冲突的情况
        if (e.code == HttpStatus.CONFLICT.value) {
            logger.warn("${subtask.trace()} job already exists, try to clean")
            val cleaned = cleanJob(jobName(subtask))
            return cleaned
        }

        logger.error("${subtask.trace()} dispatch failed\n, ${e.string()}")
        return false
    }

    private fun cleanJob(jobName: String): Boolean {
        logger.info("cleaning job[$jobName]")
        return ignoreApiException {
            val namespace = executionCluster.kubernetesProperties.namespace
            var retryTimes = 1
            var deleted = false
            while (true) {
                try {
                    batchV1Api.deleteNamespacedJob(
                        jobName,
                        namespace,
                        null,
                        null,
                        0,
                        null,
                        "Foreground",
                        null
                    )
                    batchV1Api.readNamespacedJob(jobName, namespace, null, null, null)
                } catch (e: ApiException) {
                    deleted = (e.code == HttpStatus.NOT_FOUND.value)
                }

                // 删除失败时进行重试
                if (!deleted && retryTimes < MAX_RETRY_TIMES) {
                    logger.info("job[$jobName] still exists after cleaning, retry times[$retryTimes]")
                    Thread.sleep(retryTimes * 1000L)
                    retryTimes++
                } else {
                    break
                }
            }
            logger.info("job[$jobName] clean result[$deleted]")
            deleted
        }
    }

    private fun jobName(subtask: SubScanTask) = "bkrepo-analyst-${subtask.scanner.name}-${subtask.taskId}"

    private fun ignoreApiException(action: () -> Boolean): Boolean {
        try {
            return action()
        } catch (e: ApiException) {
            logger.error("request k8s api failed\n, ${e.string()}")
        }
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KubernetesDispatcher::class.java)

        /**
         * 创建Job最大重试次数
         */
        private const val MAX_RETRY_TIMES = 3
    }
}
