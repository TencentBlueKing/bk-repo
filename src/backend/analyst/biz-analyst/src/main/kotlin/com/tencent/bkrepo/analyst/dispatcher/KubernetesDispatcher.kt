/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.analyst.configuration.KubernetesDispatcherProperties
import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.api.constant.HttpStatus
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.credentials.AccessTokenAuthentication
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.math.max
import kotlin.math.min

class KubernetesDispatcher(
    private val scannerProperties: ScannerProperties,
    private val k8sProperties: KubernetesDispatcherProperties
) : SubtaskDispatcher {

    private val batchV1Api by lazy { BatchV1Api() }

    init {
        val client = if (k8sProperties.token != null && k8sProperties.apiServer != null) {
            ClientBuilder()
                .setBasePath(k8sProperties.apiServer)
                .setAuthentication(AccessTokenAuthentication(k8sProperties.token))
                .build()
        } else {
            // 可通过KUBECONFIG环境变量设置config file路径
            Config.defaultClient()
        }
        Configuration.setDefaultApiClient(client)
    }

    override fun dispatch(subtask: SubScanTask): Boolean {
        logger.info("dispatch subtask[${subtask.taskId}] with $NAME")
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

        if (retryTimes == 0) {
            logger.error("subtask[${subtask.taskId}] dispatch failed after $MAX_RETRY_TIMES times retry")
        }

        return result
    }

    override fun clean(subtask: SubScanTask, subtaskStatus: String): Boolean {
        val shouldClean = k8sProperties.cleanJobAfterSuccess && subtaskStatus == SubScanTaskStatus.SUCCESS.name
        if (shouldClean || subtaskStatus == SubScanTaskStatus.STOPPED.name) {
            // 只清理执行成功或手动停止的Job，失败的Job需要保留用于排查问题
            return cleanJob(jobName(subtask))
        }
        return false
    }

    override fun availableCount(): Int {
        val api = CoreV1Api()
        val quota = api.listNamespacedResourceQuota(
            k8sProperties.namespace,
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
        val usedCpu = used["limits.cpu"]!!.number.toDouble()
        val usedMem = used["limits.memory"]!!.number.toLong()
        val availableCpu = limitCpu - usedCpu
        val availableMem = limitMem - usedMem
        val requireCpuPerTask = k8sProperties.limitCpu
        val requireMemPerTask = k8sProperties.limitMem.toBytes()
        return if (availableCpu < requireCpuPerTask || availableMem < requireMemPerTask) {
            0
        } else {
            minOf((availableCpu / requireCpuPerTask).toInt(), (availableMem / requireMemPerTask).toInt())
        }
    }

    override fun name(): String {
        return NAME
    }

    private fun createJob(subtask: SubScanTask): Boolean {
        val scanner = subtask.scanner
        require(scanner is StandardScanner)
        val jobName = jobName(subtask)
        val containerImage = scanner.image
        val cmd = buildCmd(subtask)
        val requestStorageSize = maxStorageSize(subtask.packageSize)
        val jobActiveDeadlineSeconds = subtask.scanner.maxScanDuration(subtask.packageSize)
        val body = v1Job {
            metadata {
                namespace = k8sProperties.namespace
                name = jobName
            }
            spec {
                backoffLimit = 0
                activeDeadlineSeconds = jobActiveDeadlineSeconds
                ttlSecondsAfterFinished = k8sProperties.jobTtlSecondsAfterFinished
                template {
                    spec {
                        addContainerItem {
                            name = jobName
                            image = containerImage
                            command = cmd
                            resources {
                                requests(
                                    cpu = k8sProperties.requestCpu,
                                    memory = k8sProperties.requestMem.toBytes(),
                                    ephemeralStorage = requestStorageSize
                                )
                                limits(
                                    cpu = k8sProperties.limitCpu,
                                    memory = k8sProperties.limitMem.toBytes(),
                                    ephemeralStorage = k8sProperties.limitStorage.toBytes()
                                )
                            }
                        }
                        restartPolicy = "Never"
                    }
                }
            }
        }

        batchV1Api.createNamespacedJob(k8sProperties.namespace, body, null, null, null)
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
            logger.warn("subtask[${subtask.taskId}] job already exists, try to clean")
            val jobName = jobName(subtask)
            val namespace = k8sProperties.namespace
            val job = batchV1Api.readNamespacedJob(jobName, namespace, null, null, null)
            val failed = job.status?.failed ?: 0
            // 只清理失败的job，因为成功的job说明结果也上报成功了，不需要再次分发
            if (failed > 0) {
                return cleanJob(jobName)
            }
        }

        logger.error("subtask[${subtask.taskId}] dispatch failed\n, ${e.string()}")
        return false
    }

    private fun cleanJob(jobName: String): Boolean {
        logger.info("cleaning job[$jobName]")
        return ignoreApiException {
            val namespace = k8sProperties.namespace
            batchV1Api.deleteNamespacedJob(
                jobName, namespace, null, null, null, null, "Foreground", null
            )
            logger.info("job[$jobName] clean success")
            true
        }
    }

    private fun jobName(subtask: SubScanTask) = "bkrepo-analyst-${subtask.scanner.name}-${subtask.taskId}"

    private fun buildCmd(subtask: SubScanTask): List<String> {
        val scanner = subtask.scanner
        require(scanner is StandardScanner)
        val cmd = ArrayList<String>()
        cmd.addAll(scanner.cmd.split(" "))
        cmd.add("--url")
        cmd.add(scannerProperties.baseUrl)
        cmd.add("--task-id")
        cmd.add(subtask.taskId)
        cmd.add("--token")
        cmd.add(subtask.token!!)
        return cmd
    }

    private fun maxStorageSize(fileSize: Long): Long {
        val requestStorage = max(k8sProperties.requestStorage.toBytes(), fileSize * MAX_FILE_SIZE_MULTIPLIER)
        return min(k8sProperties.limitStorage.toBytes(), requestStorage)
    }

    private fun ignoreApiException(action: () -> Boolean): Boolean {
        try {
            return action()
        } catch (e: ApiException) {
            logger.error("request k8s api failed\n, ${e.string()}")
        }
        return false
    }

    private fun ApiException.string(): String {
        return "message: $message\n" +
            "code: $code\n" +
            "headers: $responseHeaders\n" +
            "body: $responseBody"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KubernetesDispatcher::class.java)

        /**
         * 最大允许的单文件大小为待扫描文件大小3倍
         */
        private const val MAX_FILE_SIZE_MULTIPLIER = 3L

        /**
         * 创建Job最大重试次数
         */
        private const val MAX_RETRY_TIMES = 3
        const val NAME = "k8s"
    }
}
