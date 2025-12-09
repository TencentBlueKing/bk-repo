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

package com.tencent.bkrepo.replication.replica.type

import com.google.common.base.Throwables
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.replication.dao.ReplicaFailureRecordDao
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.metrics.ReplicationRecord
import com.tencent.bkrepo.replication.pojo.record.ExecutionResult
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.request.RecordDetailInitialRequest
import com.tencent.bkrepo.replication.pojo.request.PackageVersionDeleteSummary
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.TaskExecuteType
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.pojo.task.setting.ConflictStrategy
import com.tencent.bkrepo.replication.pojo.task.setting.ErrorStrategy
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.context.ReplicaExecutionContext
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.util.ReplicationMetricsRecordUtil.convertToReplicationRecordDetailMetricsRecord
import com.tencent.bkrepo.replication.util.ReplicationMetricsRecordUtil.toJson
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 同步服务抽象类
 * 一次replica执行负责一个任务下的一个集群，在子线程中执行
 */
@Suppress("TooGenericExceptionCaught")
abstract class AbstractReplicaService(
    private val replicaRecordService: ReplicaRecordService,
    val localDataManager: LocalDataManager,
    private val replicaFailureRecordDao: ReplicaFailureRecordDao,
) : ReplicaService {

    /**
     * 同步 task Object
     */
    protected fun replicaTaskObjects(replicaContext: ReplicaContext) {
        with(replicaContext) {
            // 检查版本
            replicator.checkVersion(this)
            if (task.setting.automaticCreateRemoteRepo) {
                // 同步项目信息
                replicator.replicaProject(replicaContext)
                // 同步仓库信息
                replicator.replicaRepo(replicaContext)
            }
            // 按仓库同步
            if (includeAllData(this)) {
                replicaContext.executeType = TaskExecuteType.FULL
                replicaByRepo(this)
                return
            }
            replicaContext.executeType = TaskExecuteType.PARTIAL
            replicaTaskObjectConstraints(this)
        }
    }


    /**
     * 判断是否包含所有仓库数据，进行仓库同步
     */
    open fun includeAllData(context: ReplicaContext): Boolean {
        return false
    }

    /**
     * 同步task object 中的包列表或者paths
     */
    open fun replicaTaskObjectConstraints(replicaContext: ReplicaContext) {
        with(replicaContext) {
            // 按包同步
            taskObject.packageConstraints.orEmpty().forEach {
                replicaByPackageConstraint(this, it)
            }
            // 按路径同步
            taskObject.pathConstraints.orEmpty().forEach {
                replicaByPathConstraint(this, it)
            }
        }
    }


    /**
     * 同步整个仓库数据
     */
    protected fun replicaByRepo(replicaContext: ReplicaContext) {
        if (replicaContext.taskObject.repoType == RepositoryType.GENERIC) {
            // 同步generic节点
            val root = localDataManager.findNodeDetail(
                projectId = replicaContext.localProjectId,
                repoName = replicaContext.localRepoName,
                fullPath = PathUtils.ROOT
            ).nodeInfo
            replicaByPath(replicaContext, root)
            logger.info(
                "replicaByRepo for generic finished" +
                    " ${replicaContext.localProjectId}|${replicaContext.localRepoName}"
            )
            return
        }
        // 同步包
        val option = PackageListOption(pageNumber = 1, pageSize = PAGE_SIZE)
        var packages = localDataManager.listPackagePage(
            projectId = replicaContext.localProjectId,
            repoName = replicaContext.localRepoName,
            option = option
        )
        while (packages.isNotEmpty()) {
            packages.forEach {
                replicaByPackage(replicaContext, it)
            }
            option.pageNumber += 1
            packages = localDataManager.listPackagePage(
                projectId = replicaContext.localProjectId,
                repoName = replicaContext.localRepoName,
                option = option
            )
        }
        logger.info("replicaByRepo finished ${replicaContext.localProjectId}|${replicaContext.localRepoName}")
    }

    /**
     * 同步指定包的数据
     */
    protected fun replicaByPackageConstraint(replicaContext: ReplicaContext, constraint: PackageConstraint) {
        with(replicaContext) {
            try {
                // 查询本地包信息
                val packageSummary = localDataManager.findPackageByKey(
                    projectId = localProjectId,
                    repoName = taskObject.localRepoName,
                    packageKey = constraint.packageKey!!
                )
                replicaByPackage(this, packageSummary, constraint.versions)
            } catch (throwable: Throwable) {
                setRunOnceTaskFailedRecordMetrics(this, throwable, packageConstraint = constraint)
                throw throwable
            }
        }
    }

    /**
     * 同步指定路径的数据
     */
    protected fun replicaByPathConstraint(replicaContext: ReplicaContext, constraint: PathConstraint) {
        with(replicaContext) {
            try {
                val nodeInfo = localDataManager.findNode(
                    projectId = localProjectId,
                    repoName = localRepoName,
                    fullPath = constraint.path!!
                )?.nodeInfo
                if (nodeInfo == null) {
                    replicaByRegexPathConstraint(replicaContext, constraint)
                } else {
                    replicaByPath(this, nodeInfo)
                }
            } catch (throwable: Throwable) {
                val msg = "replicaByPathConstraint ${constraint.path} failed, error is ${throwable.message}"
                if (throwable is NodeNotFoundException) {
                    logger.warn(msg)
                } else {
                    logger.error(msg)
                }
                setRunOnceTaskFailedRecordMetrics(this, throwable, pathConstraint = constraint)
                throw throwable
            }
        }
    }

    private fun replicaByRegexPathConstraint(replicaContext: ReplicaContext, constraint: PathConstraint) {
        with(replicaContext) {
            localDataManager.processRegexPathNodes(
                projectId = replicaContext.localProjectId,
                repoName = replicaContext.localRepoName,
                fullPath = constraint.path!!,
                nodeProcessor = { node ->
                    replicaByPath(this, node)
                }
            )
        }
    }

    /**
     * 同步目录数据(只同步当前目录)
     */
    protected fun replicaFolderOnlyByPathConstraint(replicaContext: ReplicaContext, constraint: PathConstraint) {
        with(replicaContext) {
            try {
                val nodeInfo = localDataManager.findNodeDetail(
                    projectId = localProjectId,
                    repoName = localRepoName,
                    fullPath = constraint.path!!
                ).nodeInfo
                replicaFolderNodeOnly(replicaContext, nodeInfo)
            } catch (throwable: Throwable) {
                val msg = "replicaFolderOnlyByPathConstraint ${constraint.path} failed, error is ${throwable.message}"
                if (throwable is NodeNotFoundException) {
                    logger.warn(msg)
                } else {
                    logger.error(msg)
                }
                throw throwable
            }
        }
    }

    /**
     * 同步删除package数据
     */
    protected fun replicaByDeletedPackage(
        replicaContext: ReplicaContext,
        packageVersionDeleteSummary: PackageVersionDeleteSummary
    ) {
        val packageKey = packageVersionDeleteSummary.packageKey
        val versionName = packageVersionDeleteSummary.versionName
        try {
            replicaDeletedPackage(replicaContext, packageVersionDeleteSummary)
        } catch (throwable: Throwable) {
            logger.error(
                "replicaByDeletedPackage $packageKey|$versionName failed, error: ${throwable.message}"
            )
            throw throwable
        }
    }

    /**
     * 同步删除节点数据
     */
    protected fun replicaByDeletedNode(replicaContext: ReplicaContext, constraint: PathConstraint) {
        with(replicaContext) {
            try {
                val nodeInfo = if (constraint.deletedDate == null) {
                    localDataManager.findDeletedNodeDetail(
                        projectId = localProjectId,
                        repoName = localRepoName,
                        fullPath = constraint.path!!,
                    )?.nodeInfo
                } else {
                    localDataManager.findDeletedNodeDetail(
                        projectId = localProjectId,
                        repoName = localRepoName,
                        fullPath = constraint.path!!,
                        deleted = LocalDateTime.parse(constraint.deletedDate, DateTimeFormatter.ISO_DATE_TIME)
                    )?.nodeInfo
                } ?: return
                replicaDeletedNode(this, nodeInfo)
            } catch (throwable: Throwable) {
                logger.error("replicaByDeletedNode ${constraint.path} failed, error is ${throwable.message}")
                setRunOnceTaskFailedRecordMetrics(this, throwable, pathConstraint = constraint)
                throw throwable
            }
        }
    }

    /**
     * 同步移动或复制的节点
     */
    protected fun replicaByMovedOrCopiedNode(
        replicaContext: ReplicaContext,
        nodeOrMoveRequest: NodeMoveCopyRequest,
        move: Boolean
    ) {
        with(replicaContext) {
            try {
                replicaMovedOrCopiedNode(this, nodeOrMoveRequest, move)
            } catch (throwable: Throwable) {
                logger.error(
                    "replicaByMovedOrCopiedNode ${nodeOrMoveRequest.srcFullPath} " +
                        "to ${nodeOrMoveRequest.destFullPath} failed, error is ${throwable.message}"
                )
                throw throwable
            }
        }
    }

    /**
     * 同步rename的节点
     */
    protected fun replicaByRenamedNode(replicaContext: ReplicaContext, nodeRenameRequest: NodeRenameRequest) {
        with(replicaContext) {
            try {
                replicaRenamedNode(this, nodeRenameRequest)
            } catch (throwable: Throwable) {
                logger.error(
                    "replicaByRenamedNode ${nodeRenameRequest.fullPath} " +
                        "to ${nodeRenameRequest.newFullPath} failed, error is ${throwable.message}"
                )
                throw throwable
            }
        }
    }

    /**
     * 同步更新的元数据
     */
    protected fun replicaBySaveMetadata(replicaContext: ReplicaContext, metadataSaveRequest: MetadataSaveRequest) {
        with(replicaContext) {
            try {
                replicaSavedMetadata(this, metadataSaveRequest)
            } catch (throwable: Throwable) {
                logger.error(
                    "replicaBySaveMetadata for ${metadataSaveRequest.fullPath} failed, error is ${throwable.message}"
                )
                throw throwable
            }
        }
    }

    /**
     * 同步删除元数据
     */
    protected fun replicaByDeleteMetadata(
        replicaContext: ReplicaContext, metadataDeleteRequest: MetadataDeleteRequest
    ) {
        with(replicaContext) {
            try {
                replicaDeletedMetadata(this, metadataDeleteRequest)
            } catch (throwable: Throwable) {
                logger.error(
                    "replicaByDeleteMetadata for ${metadataDeleteRequest.fullPath} failed," +
                        " error is ${throwable.message}"
                )
                throw throwable
            }
        }
    }

    /**
     * 同步路径
     * 采用广度优先遍历
     */
    private fun replicaByPath(replicaContext: ReplicaContext, node: NodeInfo) {
        if (!node.folder) {
            replicaFileNode(replicaContext, node)
            return
        }
        replicaFolderNode(replicaContext, node)
    }

    /**
     * 同步文件节点
     */
    private fun replicaFileNode(replicaContext: ReplicaContext, node: NodeInfo) {
        with(replicaContext) {
            // 如果节点来源不属于此次任务限制的来源，则跳过
            val sourceFilter = replicaContext.taskObject.sourceFilter
            if (!sourceFilter.isNullOrEmpty() && !node.federatedSource.isNullOrEmpty()) {
                if (node.federatedSource !in sourceFilter) {
                    logger.info(
                        "Node ${node.fullPath} in repo ${node.projectId}|${node.repoName}" +
                            " is not in source filter list"
                    )
                    return
                }
            }
            // 存在冲突：记录冲突策略
            val conflictStrategy = if (
                !remoteProjectId.isNullOrBlank() && !remoteRepoName.isNullOrBlank() &&
                replicaContext.replicator.checkNodeExist(
                    context = replicaContext,
                    projectId = remoteProjectId,
                    repoName = remoteRepoName,
                    fullPath = node.fullPath,
                    deleted = node.deleted
                )
            ) {
                replicaProgress.conflict++
                task.setting.conflictStrategy
            } else null
            val replicaExecutionContext = initialExecutionContext(
                context = replicaContext,
                artifactName = node.fullPath,
                conflictStrategy = conflictStrategy,
                size = node.size,
                sha256 = node.sha256
            )
            replicaExecutionContext.replicaContext.recordDetailId == replicaExecutionContext.detail.id
            replicaFile(replicaExecutionContext, node)
        }
    }

    /**
     * 同步文件夹节点（子节点遍历）
     */
    private fun replicaFolderNode(replicaContext: ReplicaContext, node: NodeInfo) {
        with(replicaContext) {
            // 判断是否需要同步已删除的节点
            val includeDeleted =
                taskDetail.task.replicaType == ReplicaType.FEDERATION && executeType != TaskExecuteType.DELTA
            // 查询子节点
            var pageNumber = DEFAULT_PAGE_NUMBER
            var nodes = localDataManager.listNodePage(
                projectId = replicaContext.localProjectId,
                repoName = replicaContext.localRepoName,
                fullPath = node.fullPath,
                pageNumber = pageNumber,
                pageSize = PAGE_SIZE,
                includeDeleted = includeDeleted
            )
            while (nodes.isNotEmpty()) {
                nodes.forEach {
                    replicaByPath(this, it)
                }
                pageNumber++
                nodes = localDataManager.listNodePage(
                    projectId = replicaContext.localProjectId,
                    repoName = replicaContext.localRepoName,
                    fullPath = node.fullPath,
                    pageNumber = pageNumber,
                    pageSize = PAGE_SIZE,
                    includeDeleted = includeDeleted
                )
            }
        }
    }

    /**
     * 同步文件夹节点（只同步当前文件夹节点）
     */
    private fun replicaFolderNodeOnly(replicaContext: ReplicaContext, node: NodeInfo) {
        with(replicaContext) {
            val record = ReplicationRecord(path = node.fullPath)
            val replicaExecutionContext = initialExecutionContext(
                context = replicaContext,
                artifactName = node.fullPath,
            )
            runActionAndPrintLog(replicaExecutionContext, record) {
                replicaContext.replicator.replicaDir(replicaContext, node)
                true
            }
        }
    }

    /**
     * 同步删除节点
     */
    private fun replicaDeletedNode(replicaContext: ReplicaContext, node: NodeInfo) {
        with(replicaContext) {
            val record = ReplicationRecord(path = node.fullPath)
            val replicaExecutionContext = initialExecutionContext(
                context = replicaContext,
                artifactName = node.fullPath,
            )
            runActionAndPrintLog(replicaExecutionContext, record) {
                replicaContext.replicator.replicaDeletedNode(replicaContext, node)
            }
        }
    }

    /**
     * 同步节点
     */
    private fun replicaFile(context: ReplicaExecutionContext, node: NodeInfo) {
        with(context) {
            val record = ReplicationRecord(
                path = node.fullPath,
                sha256 = node.sha256,
                size = node.size.toString(),
                deleted = node.deleted
            )
            val fullPath = "${node.projectId}/${node.repoName}${node.fullPath}"
            runActionAndPrintLog(context, record, true) {
                replicaContext.replicator.replicaFile(replicaContext, node)
            }
        }
    }


    /**
     * 同步删除package
     */
    private fun replicaDeletedPackage(
        replicaContext: ReplicaContext,
        packageVersionDeleteSummary: PackageVersionDeleteSummary
    ) {
        if (packageVersionDeleteSummary.packageKey.isEmpty()) return
        val record = ReplicationRecord(
            packageName = packageVersionDeleteSummary.packageKey,
            version = packageVersionDeleteSummary.versionName,
        )
        val replicaExecutionContext = initialExecutionContext(
            context = replicaContext,
            artifactName = packageVersionDeleteSummary.packageName,
            version = packageVersionDeleteSummary.versionName
        )
        runActionAndPrintLog(replicaExecutionContext, record) {
            replicaContext.replicator.replicaDeletedPackage(replicaContext, packageVersionDeleteSummary)
        }
    }

    /**
     * 根据[packageSummary]和版本列表[versionNames]执行同步
     */
    private fun replicaByPackage(
        replicaContext: ReplicaContext,
        packageSummary: PackageSummary,
        versionNames: List<String>? = null,
    ) {
        replicaContext.replicator.replicaPackage(replicaContext, packageSummary)
        // 同步package功能： 对应内部集群配置是当version不存在时则同步全部的package version
        // 而对于外部集群配置而言，当version不存在时，则不进行同步
        val versions = versionNames?.map {
            localDataManager.findPackageVersion(
                projectId = replicaContext.localProjectId,
                repoName = replicaContext.localRepoName,
                packageKey = packageSummary.key,
                version = it
            )
        } ?: run {
            if (replicaContext.remoteCluster.type == ClusterNodeType.REMOTE) {
                emptyList()
            } else {
                localDataManager.listAllVersion(
                    projectId = replicaContext.localProjectId,
                    repoName = replicaContext.localRepoName,
                    packageKey = packageSummary.key,
                    option = VersionListOption()
                )
            }
        }
        versions.forEach {
            // 存在冲突：记录冲突策略
            // 外部集群仓库没有project/repoName
            val conflictStrategy = if (
                !replicaContext.remoteProjectId.isNullOrBlank() && !replicaContext.remoteRepoName.isNullOrBlank()
                && replicaContext.replicator.checkPackageVersionExist(
                    context = replicaContext,
                    projectId = replicaContext.remoteProjectId,
                    repoName = replicaContext.remoteRepoName,
                    packageKey = packageSummary.key,
                    versionName = it.name
                )
            ) {
                replicaContext.replicaProgress.conflict++
                replicaContext.task.setting.conflictStrategy
            } else null
            val replicaExecutionContext = initialExecutionContext(
                context = replicaContext,
                artifactName = packageSummary.name,
                version = it.name,
                conflictStrategy = conflictStrategy,
                size = it.size
            )
            replicaPackageVersion(replicaExecutionContext, packageSummary, it)
        }
    }

    /**
     * 同步版本
     */
    private fun replicaPackageVersion(
        context: ReplicaExecutionContext,
        packageSummary: PackageSummary,
        version: PackageVersion,
    ) {
        with(context) {
            val record = ReplicationRecord(
                packageName = packageSummary.key,
                version = version.name,
                size = version.size.toString(),
            )
            runActionAndPrintLog(context, record, true) {
                replicator.replicaPackageVersion(replicaContext, packageSummary, version)
            }
        }
    }

    /**
     * 同步移动或复制的节点
     */
    private fun replicaMovedOrCopiedNode(
        replicaContext: ReplicaContext,
        nodeOrMoveRequest: NodeMoveCopyRequest,
        move: Boolean
    ) {
        with(replicaContext) {
            val record = ReplicationRecord(path = nodeOrMoveRequest.srcFullPath)
            val replicaExecutionContext = initialExecutionContext(
                context = replicaContext,
                artifactName = nodeOrMoveRequest.srcFullPath
            )
            runActionAndPrintLog(replicaExecutionContext, record) {
                if (move) {
                    replicator.replicaNodeMove(replicaContext, nodeOrMoveRequest)
                } else {
                    replicator.replicaNodeCopy(replicaContext, nodeOrMoveRequest)
                }
            }
        }
    }

    /**
     * 同步rename的节点
     */
    private fun replicaRenamedNode(replicaContext: ReplicaContext, nodeRenameRequest: NodeRenameRequest) {
        with(replicaContext) {
            val record = ReplicationRecord(path = nodeRenameRequest.fullPath)
            val replicaExecutionContext = initialExecutionContext(
                context = replicaContext,
                artifactName = nodeRenameRequest.fullPath
            )
            runActionAndPrintLog(replicaExecutionContext, record) {
                replicator.replicaNodeRename(replicaContext, nodeRenameRequest)
            }
        }
    }

    /**
     * 同步更新元数据
     */
    private fun replicaSavedMetadata(replicaContext: ReplicaContext, metadataSaveRequest: MetadataSaveRequest) {
        with(replicaContext) {
            val record = ReplicationRecord(path = metadataSaveRequest.fullPath)
            val replicaExecutionContext = initialExecutionContext(
                context = replicaContext,
                artifactName = metadataSaveRequest.fullPath
            )
            runActionAndPrintLog(replicaExecutionContext, record) {
                replicator.replicaMetadataSave(replicaContext, metadataSaveRequest)
            }
        }
    }

    /**
     * 同步删除元数据
     */
    private fun replicaDeletedMetadata(replicaContext: ReplicaContext, metadataDeleteRequest: MetadataDeleteRequest) {
        with(replicaContext) {
            val record = ReplicationRecord(path = metadataDeleteRequest.fullPath)
            val replicaExecutionContext = initialExecutionContext(
                context = replicaContext,
                artifactName = metadataDeleteRequest.fullPath
            )
            runActionAndPrintLog(replicaExecutionContext, record) {
                replicator.replicaMetadataDelete(replicaContext, metadataDeleteRequest)
            }
        }
    }

    private fun runActionAndPrintLog(
        context: ReplicaExecutionContext,
        record: ReplicationRecord,
        checkConflictStrategy: Boolean = false,
        action: () -> Boolean
    ) {
        val startTime = LocalDateTime.now().toString()
        var status: ExecutionStatus = ExecutionStatus.SUCCESS
        var errorReason: String? = null
        
        try {
            status = executeActionIfNeeded(context, checkConflictStrategy, record, action)
        } catch (throwable: Throwable) {
            status = ExecutionStatus.FAILED
            errorReason = throwable.message.orEmpty()
            handleExecutionError(context, throwable, record)
        } finally {
            with(context) {
                finalizeRecordDetail(context)
                setRunOnceTaskRecordMetrics(
                    task = replicaContext.task,
                    recordId = detail.recordId,
                    startTime = startTime,
                    errorReason = errorReason,
                    status = status,
                    record = record
                )
            }
        }
    }

    /**
     * 执行操作（如果需要）
     */
    private fun executeActionIfNeeded(
        context: ReplicaExecutionContext,
        checkConflictStrategy: Boolean,
        record: ReplicationRecord,
        action: () -> Boolean
    ): ExecutionStatus {
        val shouldExecute = determineShouldExecute(context, checkConflictStrategy, record)
        return if (shouldExecute) {
            executeActionAndUpdateProgress(action, context)
        } else {
            ExecutionStatus.SUCCESS
        }
    }

    /**
     * 判断是否应该执行操作
     */
    private fun determineShouldExecute(
        context: ReplicaExecutionContext,
        checkConflictStrategy: Boolean,
        record: ReplicationRecord
    ): Boolean {
        if (!checkConflictStrategy) {
            return true
        }
        
        return when (context.detail.conflictStrategy) {
            ConflictStrategy.SKIP -> {
                context.replicaContext.replicaProgress.skip++
                false
            }
            ConflictStrategy.FAST_FAIL -> {
                throw IllegalArgumentException("File $record conflict.")
            }
            else -> true
        }
    }

    /**
     * 执行操作并更新进度
     */
    private fun executeActionAndUpdateProgress(
        action: () -> Boolean,
        context: ReplicaExecutionContext
    ): ExecutionStatus {
        val executed = action()
        return if (executed) {
            context.replicaContext.replicaProgress.success++
            ExecutionStatus.SUCCESS
        } else {
            context.replicaContext.replicaProgress.failed++
            ExecutionStatus.FAILED
        }
    }

    /**
     * 处理执行错误
     */
    private fun handleExecutionError(
        context: ReplicaExecutionContext,
        throwable: Throwable,
        record: ReplicationRecord
    ) {
        logger.error(
            "replica file failed, error is ${Throwables.getStackTraceAsString(throwable)}"
        )
        context.replicaContext.replicaProgress.failed++
        setErrorStatus(context, throwable)

        val willThrow = shouldFastFail(context.replicaContext)
        if (willThrow) {
            throw throwable
        }
        
        // 记录分发失败
        recordFailureToDatabase(context, throwable, record)
    }

    /**
     * 完成记录详情
     */
    private fun finalizeRecordDetail(context: ReplicaExecutionContext) {
        if (context.replicaContext.task.record == false) {
            replicaRecordService.deleteRecordDetailById(context.detail.id)
        } else {
            completeRecordDetail(context)
        }
    }

    private fun shouldFastFail(context: ReplicaContext): Boolean {
        if (context.executeType == TaskExecuteType.FULL) {
            return false
        }
        return context.task.setting.errorStrategy == ErrorStrategy.FAST_FAIL
    }

    /**
     * 记录因需要分发的package或者path本地不存在而导致的异常，分发中的异常已在实际分发处记录
     */
    private fun setRunOnceTaskFailedRecordMetrics(
        context: ReplicaContext,
        throwable: Throwable,
        packageConstraint: PackageConstraint? = null,
        pathConstraint: PathConstraint? = null,
    ) {
        with(context) {
            if (throwable !is IllegalStateException) return
            val record = ReplicationRecord(
                packageName = packageConstraint?.packageKey,
                path = pathConstraint?.path
            )
            setRunOnceTaskRecordMetrics(
                task = task,
                recordId = taskRecord.id,
                startTime = LocalDateTime.now().toString(),
                errorReason = throwable.message.orEmpty(),
                status = ExecutionStatus.FAILED,
                record = record
            )
        }
    }

    /**
     * 记录一次性任务执行package或者path分发的执行记录
     */
    private fun setRunOnceTaskRecordMetrics(
        task: ReplicaTaskInfo,
        recordId: String,
        startTime: String,
        status: ExecutionStatus,
        errorReason: String? = null,
        record: ReplicationRecord,
    ) {
        logger.info(
            toJson(
                convertToReplicationRecordDetailMetricsRecord(
                    task = task,
                    recordId = recordId,
                    startTime = startTime,
                    status = status,
                    errorReason = errorReason,
                    packageName = record.packageName,
                    version = record.version,
                    path = record.path,
                    sha256 = record.sha256,
                    size = record.size
                )
            )
        )
    }

    /**
     * 初始化执行过程context
     */
    private fun initialExecutionContext(
        context: ReplicaContext,
        packageConstraint: PackageConstraint? = null,
        pathConstraint: PathConstraint? = null,
        artifactName: String,
        version: String? = null,
        conflictStrategy: ConflictStrategy? = null,
        size: Long? = null,
        sha256: String? = null,
    ): ReplicaExecutionContext {
        // 创建详情
        val request = RecordDetailInitialRequest(
            recordId = context.taskRecord.id,
            remoteCluster = context.remoteCluster.name,
            localRepoName = context.localRepoName,
            repoType = context.localRepoType,
            packageConstraint = packageConstraint,
            pathConstraint = pathConstraint,
            artifactName = artifactName,
            version = version,
            conflictStrategy = conflictStrategy,
            size = size,
            sha256 = sha256,
            executeType = context.executeType
        )
        val recordDetail = replicaRecordService.initialRecordDetail(request)
        return ReplicaExecutionContext(context, recordDetail)
    }

    /**
     * 设置状态为失败状态
     */
    private fun setErrorStatus(context: ReplicaExecutionContext, throwable: Throwable) {
        context.status = ExecutionStatus.FAILED
        context.appendErrorReason(throwable.message.orEmpty())
        context.replicaContext.status = ExecutionStatus.FAILED
        context.replicaContext.errorMessage = throwable.message.orEmpty()
    }

    /**
     * 记录分发失败到数据库
     */
    private fun recordFailureToDatabase(
        context: ReplicaExecutionContext,
        throwable: Throwable,
        record: ReplicationRecord
    ) {
        try {
            val replicaContext = context.replicaContext
            if (replicaContext.task.replicaType == ReplicaType.RUN_ONCE) {
                return
            }

            val artifactEvent = getArtifactEventSafely(replicaContext)
            if (artifactEvent != null) {
                // event类型的失败在event_record中会进行重试处理
                return
            }

            val (packageConstraint, pathConstraint) = buildConstraintsForFailureRecord(
                replicaContext = replicaContext,
                context = context,
                record = record
            )

            recordFailureToRepository(
                replicaContext = replicaContext,
                packageConstraint = packageConstraint,
                pathConstraint = pathConstraint,
                throwable = throwable,
            )
        } catch (e: Exception) {
            logger.warn("Failed to record failure to database", e)
        }
    }

    /**
     * 构建失败记录的约束条件
     */
    private fun buildConstraintsForFailureRecord(
        replicaContext: ReplicaContext,
        context: ReplicaExecutionContext,
        record: ReplicationRecord
    ): Pair<PackageConstraint?, PathConstraint?> {
        // 如果是仓库类型，需要从record中构建约束条件
        if (replicaContext.task.replicaObjectType != ReplicaObjectType.REPOSITORY) {
            return Pair(context.detail.packageConstraint, context.detail.pathConstraint)
        }

        // 优先使用路径约束
        if (!record.path.isNullOrEmpty()) {
            return Pair(null, PathConstraint(path = record.path, deletedDate = record.deleted))
        }

        // 如果路径为空，尝试使用包约束
        if (record.packageName.isNullOrEmpty() || record.version.isNullOrEmpty()) {
            return Pair(null, null)
        }

        return Pair(
            PackageConstraint(packageKey = record.packageName, versions = listOf(record.version!!)),
            null
        )
    }

    /**
     * 安全获取 artifactEvent
     */
    private fun getArtifactEventSafely(replicaContext: ReplicaContext): ArtifactEvent? {
        return try {
            replicaContext.event
        } catch (e: Exception) {
            null
        }
    }


    /**
     * 记录失败信息到仓库
     */
    private fun recordFailureToRepository(
        replicaContext: ReplicaContext,
        packageConstraint: PackageConstraint?,
        pathConstraint: PathConstraint?,
        throwable: Throwable,
    ) {
        with(replicaContext) {
            replicaFailureRecordDao.recordFailure(
                taskKey = task.key,
                remoteClusterId = remoteCluster.id!!,
                projectId = localProjectId,
                localRepoName = localRepoName,
                remoteProjectId = remoteProjectId ?: "",
                remoteRepoName = remoteRepoName ?: "",
                failureType = task.replicaObjectType,
                packageConstraint = packageConstraint,
                pathConstraint = pathConstraint,
                failureReason = throwable.message ?: "Unknown error",
                failedRecordId = failedRecordId
            )
            logger.info(
                "Recorded failure for task[${task.key}],  reason[${throwable.message}]"
            )
        }
    }

    /**
     * 持久化同步进度
     */
    private fun completeRecordDetail(context: ReplicaExecutionContext) {
        with(context) {
            val result = ExecutionResult(
                status = status,
                progress = progress,
                errorReason = buildErrorReason()
            )
            replicaRecordService.completeRecordDetail(detail.id, result)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractReplicaService::class.java)
        private const val PAGE_SIZE = 1000
    }
}