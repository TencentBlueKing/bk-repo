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

package com.tencent.bkrepo.scanner.service.impl

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.scanner.dao.FileScanResultDao
import com.tencent.bkrepo.scanner.dao.ScanTaskDao
import com.tencent.bkrepo.scanner.dao.SubScanTaskDao
import com.tencent.bkrepo.scanner.exception.ScanTaskNotFoundException
import com.tencent.bkrepo.scanner.model.TScanTask
import com.tencent.bkrepo.scanner.model.TSubScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTriggerType
import com.tencent.bkrepo.scanner.pojo.ScanTaskStatus
import com.tencent.bkrepo.scanner.pojo.SubScanTask
import com.tencent.bkrepo.scanner.pojo.SubScanTaskStatus
import com.tencent.bkrepo.scanner.pojo.request.ScanRequest
import com.tencent.bkrepo.scanner.pojo.request.ReportResultRequest
import com.tencent.bkrepo.common.scanner.pojo.scanner.Scanner
import com.tencent.bkrepo.scanner.service.ScanService
import com.tencent.bkrepo.scanner.service.ScannerService
import com.tencent.bkrepo.scanner.task.ScanTaskScheduler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

@Service
class ScanServiceImpl @Autowired constructor(
    private val scanTaskDao: ScanTaskDao,
    private val subScanTaskDao: SubScanTaskDao,
    private val fileScanResultDao: FileScanResultDao,
    private val scannerService: ScannerService,
    private val scanTaskScheduler: ScanTaskScheduler
) : ScanService {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(rollbackFor = [Throwable::class])
    override fun scan(scanRequest: ScanRequest, triggerType: ScanTriggerType): ScanTask {
        with(scanRequest) {
            val scanner = scannerService.get(scanner)
            val now = LocalDateTime.now()
            val scanTask = scanTaskDao.save(
                TScanTask(
                    createdBy = "",
                    createdDate = now,
                    lastModifiedBy = "",
                    lastModifiedDate = now,
                    rule = rule?.toJsonString(),
                    triggerType = triggerType.name,
                    status = ScanTaskStatus.PENDING.name,
                    total = 0L,
                    scanning = 0L,
                    scanned = 0L,
                    scanner = scanner.name,
                    scannerType = scanner.type,
                    scannerVersion = scanner.version,
                    scanResultOverview = null
                )
            ).run { convert(this) }

            scanTaskScheduler.schedule(scanTask)
            return scanTask
        }
    }

    override fun task(taskId: String): ScanTask {
        return scanTaskDao.findById(taskId)?.let {
            convert(it)
        } ?: throw ScanTaskNotFoundException(taskId)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun reportResult(reportResultRequest: ReportResultRequest) {
        with(reportResultRequest) {
            // 任务已扫描过，重复上报直接返回
            val subScanTask = subScanTaskDao.findById(subTaskId) ?: return
            if (subScanTaskDao.deleteById(subTaskId).deletedCount != 1L) {
                return
            }
            logger.info("Updating file[${reportNode.sha256}] scan result")

            // 更新父任务扫描结果
            scanTaskDao.updateScanResult(parentTaskId, 1, scanResultOverview)
            if (scanTaskDao.taskFinished(parentTaskId).modifiedCount == 1L) {
                logger.info("Task[$parentTaskId] scan finished")
            }

            // 更新文件扫描结果
            val scanner = scannerService.get(subScanTask.scanner)
            fileScanResultDao.upsertResult(
                subScanTask.credentialsKey,
                subScanTask.sha256,
                parentTaskId,
                scanner,
                scanResultOverview,
                reportNode,
                startDateTime,
                finishedDateTime
            )
        }
    }

    override fun pullSubScanTask(): SubScanTask? {
        // 优先返回待执行任务，再返回超时任务
        val task = subScanTaskDao.firstCreatedOrEnqueuedTask()
            ?: subScanTaskDao.firstTimeoutTask(DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS)
            ?: return null
        subScanTaskDao.updateStatus(task.id!!, SubScanTaskStatus.EXECUTING)
        val scanner = scannerService.get(task.scanner)
        return convert(task, scanner)
    }

    private fun convert(scanTask: TScanTask): ScanTask = with(scanTask) {
        ScanTask(
            taskId = id!!,
            createdBy = createdBy,
            triggerDateTime = createdDate.format(ISO_DATE_TIME),
            startDateTime = startDateTime?.format(ISO_DATE_TIME),
            finishedDateTime = finishedDateTime?.format(ISO_DATE_TIME),
            status = status,
            rule = scanTask.rule?.readJsonString(),
            total = total,
            scanning = scanning,
            scanned = scanned,
            scanner = scanner,
            scannerType = scannerType,
            scannerVersion = scannerVersion,
            scanResultOverview = scanResultOverview
        )
    }

    private fun convert(subScanTask: TSubScanTask, scanner: Scanner): SubScanTask = with(subScanTask) {
        SubScanTask(
            taskId = id!!,
            parentScanTaskId = parentScanTaskId,
            scanner = scanner,
            sha256 = sha256,
            size = size,
            credentialsKey = credentialsKey
        )
    }

    companion object {
        // TODO 添加到配置文件中
        private const val DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS = 600L
    }
}
