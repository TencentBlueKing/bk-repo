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
import com.tencent.bkrepo.analyst.pojo.Node
import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_ARTIFACT_LAST_MODIFIED_BY
import com.tencent.bkrepo.analyst.statemachine.Action
import com.tencent.bkrepo.analyst.statemachine.subtask.SubtaskEvent
import com.tencent.bkrepo.analyst.statemachine.subtask.context.CreateSubtaskContext
import com.tencent.bkrepo.analyst.utils.SubtaskConverter
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.statemachine.Event
import com.tencent.bkrepo.statemachine.TransitResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
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
    @Lazy
    private lateinit var self: CreateSubtaskAction

    override fun execute(source: String, target: String, event: Event): TransitResult {
        val context = event.context
        require(context is CreateSubtaskContext)
        val node = context.node
        val scanTask = context.scanTask
        val storageCredentialsKey = cacheableRepositoryClient.get(node.projectId, node.repoName).storageCredentialsKey
        val state = if (event.name == SubtaskEvent.CREATE.name) {
            SubScanTaskStatus.CREATED
        } else {
            SubScanTaskStatus.BLOCKED
        }

        // 添加到扫描任务队列
        val subtask = createSubTask(scanTask, node, storageCredentialsKey, state)
        val metadata = createMetadata(scanTask.metadata, context.node)
        self.save(listOf(subtask), metadata)
        return TransitResult(state.name, subtask)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun save(
        subScanTasks: Collection<TArchiveSubScanTask>,
        metadata: List<TaskMetadata>
    ): Collection<TArchiveSubScanTask> {
        if (subScanTasks.isEmpty()) {
            return emptyList()
        }

        val tasks = archiveSubScanTaskDao.insert(subScanTasks)

        // 保存方案制品最新扫描记录
        val planArtifactLatestSubScanTasks = tasks.map { SubtaskConverter.convertToPlanSubtask(it, it.status) }
        planArtifactLatestSubScanTaskDao.replace(planArtifactLatestSubScanTasks)
        subScanTaskDao.insert(tasks.map { SubtaskConverter.convertToSubtask(it, metadata) })
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
    ): TArchiveSubScanTask {
        with(node) {
            val now = LocalDateTime.now()
            val repoInfo = cacheableRepositoryClient.get(projectId, repoName)
            return TArchiveSubScanTask(
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
                scanQuality = scanTask.scanPlan?.scanQuality
            )
        }
    }

    private fun createMetadata(oldMetadata: List<TaskMetadata>, node: Node): List<TaskMetadata> {
        val new = ArrayList(oldMetadata)
        new.add(TaskMetadata(TASK_METADATA_ARTIFACT_LAST_MODIFIED_BY, node.lastModifiedBy))
        return new
    }

    override fun support(from: String, to: String, event: String): Boolean {
        return from == SubScanTaskStatus.NEVER_SCANNED.name
                && (to == SubScanTaskStatus.CREATED.name || to == SubScanTaskStatus.BLOCKED.name)
                && (event == SubtaskEvent.CREATE.name || event == SubtaskEvent.BLOCK.name)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CreateSubtaskAction::class.java)
    }
}
