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
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.credentials.AccessTokenAuthentication
import org.slf4j.LoggerFactory

class KubernetesDispatcher(
    private val scannerProperties: ScannerProperties,
    private val k8sProperties: KubernetesDispatcherProperties
) : SubtaskDispatcher {

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
        return createJob(
            taskId = subtask.taskId,
            jobNamespace = k8sProperties.namespace,
            jobName = "bkrepo-analyst-${subtask.scanner.name}-${subtask.taskId}",
            containerImage = scanner.image,
            cmd = cmd,
            limitStorageSize = maxStorageSize(subtask.packageSize),
            jobActiveDeadlineSeconds = scanner.maxScanDuration(subtask.packageSize)
        )
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

    @Suppress("LongParameterList")
    private fun createJob(
        taskId: String,
        jobNamespace: String,
        jobName: String,
        containerImage: String,
        cmd: List<String>,
        limitStorageSize: Long,
        jobActiveDeadlineSeconds: Long,
    ): Boolean {
        val body = v1Job {
            metadata {
                namespace = jobNamespace
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
                                    ephemeralStorage = limitStorageSize
                                )
                                limits(
                                    cpu = k8sProperties.limitCpu,
                                    memory = k8sProperties.limitMem.toBytes(),
                                    ephemeralStorage = limitStorageSize
                                )
                            }
                        }
                        restartPolicy = "Never"
                    }
                }
            }
        }

        try {
            val api = BatchV1Api()
            api.createNamespacedJob(jobNamespace, body, null, null, null)
            logger.info("dispatch subtask[$taskId] success")
            return true
        } catch (e: ApiException) {
            logger.error(
                "subtask[$taskId] dispatch failed\n," +
                    " code: ${e.code}\nmessage: ${e.message}\nheaders: ${e.responseHeaders}\nbody: ${e.responseBody}"
            )
        }
        return false
    }

    private fun maxStorageSize(fileSize: Long): Long {
        // 最大允许的单文件大小为待扫描文件大小3倍，先除以3，防止long溢出
        return (Long.MAX_VALUE / 3L).coerceAtMost(fileSize) * 3L
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KubernetesDispatcher::class.java)
        const val NAME = "k8s"
    }
}
