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

package com.tencent.bkrepo.analyst.statemachine.subtask.action

import com.tencent.bkrepo.analyst.component.CacheableRepositoryClient
import com.tencent.bkrepo.analyst.dao.ArchiveSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.PlanArtifactLatestSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.event.SubtaskStatusChangedEvent
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.model.TArchiveSubScanTask
import com.tencent.bkrepo.analyst.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.Node
import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.CreateSubtaskContext
import com.tencent.bkrepo.analyst.statemachine.subtask.context.SubtaskContext
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Action
@Suppress("LongParameterList")
class CreateSubtaskAction(
    private val cacheableRepositoryClient: CacheableRepositoryClient,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val publisher: ApplicationEventPublisher,
    private val scannerMetrics: ScannerMetrics,
    private val subScanTaskDao: SubScanTaskDao
) : SubtaskAction {

    @Autowired
    private lateinit var self: CreateSubtaskAction

    override fun execute(
        from: SubScanTaskStatus,
        to: SubScanTaskStatus,
        event: SubtaskEvent,
        context: SubtaskContext
    ) {
        require(context is CreateSubtaskContext)
        val node = context.node
        val scanTask = context.scanTask
        val storageCredentialsKey = cacheableRepositoryClient.get(node.projectId, node.repoName).storageCredentialsKey
        val state = if (event == SubtaskEvent.CREATE) {
            SubScanTaskStatus.CREATED
        } else {
            SubScanTaskStatus.BLOCKED
        }

        // 添加到扫描任务队列
        val subtask = createSubTask(scanTask, node, storageCredentialsKey, state)
        self.save(listOf(subtask))
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun save(subScanTasks: Collection<TSubScanTask>): Collection<TSubScanTask> {
        if (subScanTasks.isEmpty()) {
            return emptyList()
        }

        val tasks = subScanTaskDao.insert(subScanTasks)

        // 保存方案制品最新扫描记录
        val planArtifactLatestSubScanTasks = tasks.map { TPlanArtifactLatestSubScanTask.convert(it, it.status) }
        planArtifactLatestSubScanTaskDao.replace(planArtifactLatestSubScanTasks)
        archiveSubScanTaskDao.insert(tasks.map { TArchiveSubScanTask.from(it, it.status) })
        planArtifactLatestSubScanTasks.forEach { publisher.publishEvent(SubtaskStatusChangedEvent(null, it)) }

        // 统计BLOCKED与CREATED任务数量
        val createdTasks = tasks.filter { it.status == SubScanTaskStatus.CREATED.name }
        val blockedTaskCount = tasks.size - createdTasks.size
        if (blockedTaskCount != 0) {
            scannerMetrics.incSubtaskCountAndGet(SubScanTaskStatus.BLOCKED, blockedTaskCount.toDouble())
        }
        scannerMetrics.incSubtaskCountAndGet(SubScanTaskStatus.CREATED, createdTasks.size.toDouble())
        logger.info("${createdTasks.size} created subtasks and $blockedTaskCount blocked subtasks saved")

        return createdTasks
    }

    private fun createSubTask(
        scanTask: ScanTask,
        node: Node,
        credentialKey: String? = null,
        status: SubScanTaskStatus = SubScanTaskStatus.CREATED
    ): TSubScanTask {
        with(node) {
            val now = LocalDateTime.now()
            val repoInfo = cacheableRepositoryClient.get(projectId, repoName)
            return TSubScanTask(
                createdBy = scanTask.createdBy,
                createdDate = now,
                lastModifiedBy = scanTask.createdBy,
                lastModifiedDate = now,

                triggerType = scanTask.triggerType,
                parentScanTaskId = scanTask.taskId,
                planId = scanTask.scanPlan?.id,

                projectId = projectId,
                repoName = repoName,
                repoType = repoInfo.type.name,
                packageKey = packageKey,
                version = packageVersion,
                fullPath = fullPath,
                artifactName = artifactName,

                status = status.name,
                executedTimes = 0,
                scanner = scanTask.scanner,
                scannerType = scanTask.scannerType,
                sha256 = sha256,
                size = size,
                packageSize = packageSize,
                credentialsKey = credentialKey,
                scanQuality = scanTask.scanPlan?.scanQuality,
                metadata = scanTask.metadata
            )
        }
    }

    override fun support(from: SubScanTaskStatus, to: SubScanTaskStatus, event: SubtaskEvent): Boolean {
        return from == SubScanTaskStatus.NEVER_SCANNED &&
            (to == SubScanTaskStatus.CREATED || to == SubScanTaskStatus.BLOCKED) &&
            (event == SubtaskEvent.CREATE || event == SubtaskEvent.BLOCK)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CreateSubtaskAction::class.java)
    }
}
