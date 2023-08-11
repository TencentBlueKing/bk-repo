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

package com.tencent.bkrepo.analysis.executor

import com.sun.management.OperatingSystemMXBean
import com.tencent.bkrepo.analysis.executor.component.FileLoader
import com.tencent.bkrepo.analysis.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.analysis.executor.util.Converter
import com.tencent.bkrepo.analyst.api.ScanClient
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.request.ReportResultRequest
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class ExecutorScheduler @Autowired constructor(
    private val scanExecutorFactory: ScanExecutorFactory,
    private val fileLoader: FileLoader,
    private val scanClient: ScanClient,
    private val executor: ThreadPoolTaskExecutor,
    private val scannerExecutorProperties: ScannerExecutorProperties
) {

    init {
        if (scannerExecutorProperties.pull) {
            pullSubtaskAtFixedRate()
        }
    }

    private val executingSubtaskExecutorMap = ConcurrentHashMap<String, ScanExecutor>()
    private val operatingSystemBean by lazy { ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean }

    fun scan(subtask: SubScanTask): Boolean {
        if (!allowExecute() || scanning(subtask.taskId)) {
            return false
        }

        scanClient.updateSubScanTaskStatus(subtask.taskId, SubScanTaskStatus.EXECUTING.name)

        executingSubtaskExecutorMap[subtask.taskId] = scanExecutorFactory.get(subtask.scanner.type)
        val executingCount = executingSubtaskExecutorMap.size
        logger.info("task start, executing task count $executingCount")
        executor.execute {
            try {
                startHeartbeat(subtask.taskId, executingCount)
                doScan(subtask)
            } finally {
                executingSubtaskExecutorMap.remove(subtask.taskId)
                logger.info("task finished, executing task count ${executingSubtaskExecutorMap.size}")
            }
        }
        return true
    }

    fun stop(subtaskId: String): Boolean {
        return executingSubtaskExecutorMap[subtaskId]?.stop(subtaskId) ?: false
    }

    /**
     * 判断任务是否正在执行
     */
    fun scanning(taskId: String): Boolean {
        return executingSubtaskExecutorMap.containsKey(taskId)
    }

    /**
     * 开始发送任务心跳到制品分析服务
     */
    private fun startHeartbeat(subtaskId: String, executingCount: Int) {
        if (scannerExecutorProperties.heartbeatInterval.seconds > 0) {
            val runnable = SubtaskHeartbeatRunnable(
                this,
                scannerExecutorProperties.heartbeatInterval,
                scanClient,
                subtaskId
            )
            Thread(runnable, "subtask-heartbeat-$executingCount").start()
        }
    }

    private fun pullSubtaskAtFixedRate() {
        val runnable = {
            try {
                while (allowExecute()) {
                    val subtask = scanClient.pullSubTask().data ?: break
                    scan(subtask)
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.error("pull subtask failed", e)
            }
        }
        scheduler.scheduleAtFixedRate(runnable, INITIAL_DELAY, FIXED_DELAY, TimeUnit.MILLISECONDS)
    }

    /**
     * 是否允许执行扫描
     */
    private fun allowExecute(): Boolean {
        val executingCount = executingSubtaskExecutorMap.size

        val freeMem = operatingSystemBean.freePhysicalMemorySize
        val totalMem = operatingSystemBean.totalPhysicalMemorySize
        val freeMemPercent = freeMem.toDouble() / totalMem.toDouble()
        val memAvailable = freeMemPercent > scannerExecutorProperties.atLeastFreeMemPercent

        val workDir = File(scannerExecutorProperties.workDir)
        if (!workDir.exists()) {
            workDir.mkdirs()
        }
        val usableDiskSpacePercent = workDir.usableSpace.toDouble() / workDir.totalSpace
        val diskAvailable = usableDiskSpacePercent > scannerExecutorProperties.atLeastUsableDiskSpacePercent

        if (!memAvailable || !diskAvailable) {
            logger.warn(
                "memory[$freeMemPercent]: $freeMem / $totalMem, " +
                    "disk space[$usableDiskSpacePercent]: $usableDiskSpacePercent / ${workDir.totalSpace}"
            )
        }

        return executingCount < scannerExecutorProperties.maxTaskCount && memAvailable && diskAvailable
    }

    @Suppress("TooGenericExceptionCaught")
    private fun doScan(subtask: SubScanTask) {
        with(subtask) {
            val startTimestamp = System.currentTimeMillis()
            // 执行扫描任务
            val result = try {
                val (file, sha256) = loadFile(subtask)
                logger.info("start to scan file[$sha256]")
                val executorTask = Converter.convert(subtask, file, sha256)
                val executor = scanExecutorFactory.get(subtask.scanner.type)
                executor.scan(executorTask)
            } catch (e: Exception) {
                logger.error(
                    "scan failed, parentTaskId[$parentScanTaskId], subTaskId[$taskId], " +
                        "sha256[$sha256], scanner[${scanner.name}]]",
                    e
                )
                null
            }

            // 上报扫描结果
            val finishedTimestamp = System.currentTimeMillis()
            val timeSpent = finishedTimestamp - startTimestamp
            logger.info(
                "scan finished[${result?.scanStatus}], timeSpent[$timeSpent], size[$packageSize], " +
                    "subtaskId[$taskId], sha256[$sha256], reporting result"
            )
            report(taskId, result)
        }
    }

    private fun loadFile(subtask: SubScanTask): Pair<File, String> {
        with(subtask) {
            // 1. 加载文件
            logger.info("start load file[$sha256]")
            // 判断文件大小是否超过限制
            val fileSizeLimit = scannerExecutorProperties.fileSizeLimit.toBytes()
            if (packageSize > fileSizeLimit) {
                throw SystemErrorException(
                    CommonMessageCode.PARAMETER_INVALID,
                    "file too large, sha256[$sha256, credentials: [$credentialsKey], subtaskId[$taskId]" +
                        ", size[$packageSize], limit[$fileSizeLimit]"
                )
            }
            return fileLoader.load(subtask)
        }
    }

    private fun report(
        subtaskId: String,
        result: ScanExecutorResult? = null
    ) {
        val request = ReportResultRequest(
            subTaskId = subtaskId,
            scanStatus = result?.scanStatus ?: SubScanTaskStatus.FAILED.name,
            scanExecutorResult = result
        )
        scanClient.report(request)
    }

    companion object {
        private const val INITIAL_DELAY = 30000L
        private const val FIXED_DELAY = 3000L
        private val scheduler = Executors.newSingleThreadScheduledExecutor()
        private val logger = LoggerFactory.getLogger(ExecutorScheduler::class.java)
    }
}
