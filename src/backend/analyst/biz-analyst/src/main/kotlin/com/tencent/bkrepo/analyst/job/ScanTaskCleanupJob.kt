/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.job

import com.tencent.bkrepo.analyst.component.manager.ScanExecutorResultManager
import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.dao.ArchiveSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.FileScanResultDao
import com.tencent.bkrepo.analyst.dao.PlanArtifactLatestSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.ScanPlanDao
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.model.TArchiveSubScanTask
import com.tencent.bkrepo.analyst.model.TScanResult
import com.tencent.bkrepo.analyst.model.TScanTask
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.util.executeAndMeasureTime
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Component
class ScanTaskCleanupJob(
    private val scannerProperties: ScannerProperties,
    private val lockingTaskExecutor: LockingTaskExecutor,
    private val executor: ThreadPoolTaskExecutor,
    private val scanTaskDao: ScanTaskDao,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
    private val fileScanResultDao: FileScanResultDao,
    private val scanPlanDao: ScanPlanDao,
    private val resultManagers: Map<String, ScanExecutorResultManager>,
    private val scannerService: ScannerService,
) {
    private val executing = AtomicBoolean(false)

    @Scheduled(fixedDelay = FIXED_DELAY)
    fun clean() {
        if (executing.get()) {
            return
        }
        executor.execute {
            val lockConfiguration = LockConfiguration(
                javaClass.simpleName,
                Duration.ofDays(DEFAULT_LOCK_AT_MOST_FOR_DAYS),
                Duration.ofSeconds(1L),
            )
            lockingTaskExecutor.executeWithLock(Runnable { doClean(CleanContext()) }, lockConfiguration)
        }
    }

    private fun doClean(context: CleanContext) {
        if (!executing.compareAndSet(false, true)) {
            return
        }
        try {
            logger.info("start clean scan task")
            cleanTasks(context)
        } finally {
            val result = executing.compareAndSet(true, false)
            logger.info("finish clean scan task, set executing to false successfully: $result")
        }
    }

    private fun cleanTasks(context: CleanContext) {
        val pageSize = DEFAULT_BATCH_SIZE
        var lastId = MIN_OBJECT_ID
        val now = LocalDateTime.now()
        while (true) {
            val criteria = Criteria.where(ID).gt(lastId)
            val query = Query(criteria).limit(pageSize).with(Sort.by(ID).ascending())
            val tasks = scanTaskDao.find(query)

            var noExpiredTask = false
            // 执行清理
            for (task in tasks) {
                if (Duration.between(task.lastModifiedDate, now) < scannerProperties.reportKeepDuration) {
                    noExpiredTask = true
                    break
                }
                cleanTask(context, task)
                if (context.taskCount.get() % 1000L == 0L) {
                    logger.info("clean scan task: $context")
                }
            }

            // 没有剩余任务或剩余任务未过期时结束清理
            if (tasks.size < pageSize || noExpiredTask) {
                break
            }
            lastId = tasks.last().id!!
        }
    }

    private fun cleanTask(context: CleanContext, task: TScanTask) {
        // clean archived subtasks
        cleanArchivedSubtasks(context, task)

        // clean reports
        scanTaskDao.removeById(task.id!!)
        context.taskCount.incrementAndGet()
    }


    private fun cleanArchivedSubtasks(context: CleanContext, task: TScanTask) {
        logger.info("start clean subtask of task[${task.id}]")
        var pageRequest = Pages.ofRequest(DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE)
        while (true) {
            // 查询子任务
            val (subtasks, elapsed) = executeAndMeasureTime {
                archiveSubScanTaskDao.findByParentId(task.id!!, pageRequest).records
            }
            if (elapsed.toMillis() > MAX_QUERY_MILLIS) {
                // 查询过久时输出日志用于优化
                logger.info("clean archived subtasks of task[${task.id}] elapsed[$elapsed]")
            }

            // 执行清理
            cleanPlanSubtask(context, subtasks)
            subtasks.forEach { cleanFileOverviewResults(context, it) }
            val deletedResult = archiveSubScanTaskDao.deleteByParentTaskId(task.id!!)
            context.archivedSubtaskCount.addAndGet(deletedResult.deletedCount)

            // 判断是否需要继续清理下一页
            if (subtasks.size < pageRequest.pageSize) {
                break
            }
            pageRequest = Pages.ofRequest(pageRequest.pageNumber + 1, pageRequest.pageSize)
        }
        logger.info("finish clean subtask of task[${task.id}]")
    }

    private fun cleanPlanSubtask(context: CleanContext, latestSubtasks: List<TArchiveSubScanTask>) {
        val deletedResult = planArtifactLatestSubScanTaskDao.deleteByLatestSubtasks(latestSubtasks.map { it.id!! })
        context.planArtifactTaskCount.addAndGet(deletedResult.deletedCount)
        // 减少关联的扫描方案预览值
        scanPlanDao.decrementScanResultOverview(latestSubtasks)
    }

    private fun cleanFileOverviewResults(context: CleanContext, subtask: TArchiveSubScanTask) {
        val fileResult = fileScanResultDao.find(subtask.credentialsKey, subtask.sha256) ?: return
        val scanResult = HashMap<String, TScanResult>(fileResult.scanResult.size - 1)
        for (result in fileResult.scanResult) {
            if (result.value.taskId != subtask.parentScanTaskId) {
                scanResult[result.key] = result.value
            } else {
                // fileScanResult中存放的是最新扫描任务的预览数据，当最新任务过期时可以安全清理数据
                cleanReports(context, subtask)
            }
        }

        if (scanResult.isEmpty()) {
            // 结果为空时直接移除
            fileScanResultDao.removeById(fileResult.id!!)
        } else {
            // 更新文件扫描结果预览数据
            fileScanResultDao.updateScanResult(fileResult.id!!, fileResult.lastModifiedDate, scanResult)
        }
        context.overviewResultCount.incrementAndGet()
    }

    private fun cleanReports(context: CleanContext, subtask: TArchiveSubScanTask) {
        val scanner = scannerService.find(subtask.scanner)
        val resultManager = scanner?.let { resultManagers[it.type] }
        if (scanner == null || resultManager == null) {
            logger.error(
                "clean reports of task[${subtask.parentScanTaskId}] failed, scanner[${subtask.scanner}] not found"
            )
            return
        }
        val cleanedCount = resultManager.clean(subtask.credentialsKey, subtask.sha256, scanner)
        context.reportResultCount.addAndGet(cleanedCount)
    }

    private data class CleanContext(
        val taskCount: AtomicLong = AtomicLong(0L),
        val archivedSubtaskCount: AtomicLong = AtomicLong(0L),
        val planArtifactTaskCount: AtomicLong = AtomicLong(0L),
        val overviewResultCount: AtomicLong = AtomicLong(0L),
        val reportResultCount: AtomicLong = AtomicLong(0L),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ScanTaskCleanupJob::class.java)
        private const val FIXED_DELAY = 60 * 60 * 1000L
        private const val DEFAULT_BATCH_SIZE = 1000
        private const val DEFAULT_LOCK_AT_MOST_FOR_DAYS = 14L
        private const val MAX_QUERY_MILLIS = 5000
    }
}