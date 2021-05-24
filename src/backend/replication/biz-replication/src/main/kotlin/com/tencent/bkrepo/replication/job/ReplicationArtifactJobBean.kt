/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.replication.config.DEFAULT_VERSION
import com.tencent.bkrepo.replication.mapping.PackageNodeMappingHandler
import com.tencent.bkrepo.replication.model.TReplicationTaskLogDetail
import com.tencent.bkrepo.replication.pojo.ReplicationPackageDetail
import com.tencent.bkrepo.replication.pojo.ReplicationProjectDetail
import com.tencent.bkrepo.replication.pojo.ReplicationRepoDetail
import com.tencent.bkrepo.replication.pojo.log.ReplicationTaskLogDetail
import com.tencent.bkrepo.replication.pojo.request.NodeExistCheckRequest
import com.tencent.bkrepo.replication.pojo.request.PackageVersionExistCheckRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicationInfo
import com.tencent.bkrepo.replication.pojo.request.RepoInfo
import com.tencent.bkrepo.replication.pojo.task.ArtifactReplicationFailLevel
import com.tencent.bkrepo.replication.pojo.task.ArtifactReplicationResult
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import com.tencent.bkrepo.replication.pojo.task.setting.ConflictStrategy
import com.tencent.bkrepo.replication.repository.TaskLogDetailRepository
import com.tencent.bkrepo.replication.repository.TaskLogRepository
import com.tencent.bkrepo.replication.repository.TaskRepository
import com.tencent.bkrepo.replication.schedule.ReplicaTaskScheduler
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicationArtifactService
import com.tencent.bkrepo.replication.service.RepoDataService
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 制品级别的同步
 */
@Suppress("TooGenericExceptionCaught")
@Component
class ReplicationArtifactJobBean(
    private val taskRepository: TaskRepository,
    private val taskLogRepository: TaskLogRepository,
    private val taskLogDetailRepository: TaskLogDetailRepository,
    private val repoDataService: RepoDataService,
    private val replicationArtifactService: ReplicationArtifactService,
    private val replicaTaskScheduler: ReplicaTaskScheduler,
    private val clusterNodeService: ClusterNodeService,
    private val packageNodeMappingHandler: PackageNodeMappingHandler
) {
    @Value("\${spring.application.version}")
    private var version: String = DEFAULT_VERSION

    fun execute(taskId: String) {
        logger.info("Start to execute replication task[$taskId].")
        val task = taskRepository.findByIdOrNull(taskId) ?: run {
            logger.warn("Task[$taskId] does not exist, delete job and trigger.")
            if (replicaTaskScheduler.exist(taskId)) {
                replicaTaskScheduler.deleteJob(taskId)
            }
            return
        }
        if (task.status == ReplicationStatus.PAUSED) {
            logger.info("Task[$taskId] status is paused, skip execute.")
            return
        }
        // 点击手动执行时，如果任务正在执行中则跳过
        if (task.status == ReplicationStatus.REPLICATING) {
            logger.info("Task[$taskId] status is replicating, can't execute.")
            return
        }
        val replicationJobContext = ReplicationJobContext(task)
        replicationJobContext.masterClusterNode = clusterNodeService.getCenterNode()
        var taskSuccess = true
        try {
            // 检查本地项目是否存在
            checkProjectExists(replicationJobContext)
            // 更新task
            persistTask(replicationJobContext)
            task.replicationInfo.forEach { replicationInfo ->
                val clusterNodeList = clusterNodeService.listClusterNode(replicationInfo.remoteClusterName)
                clusterNodeList.forEach {
                    val replicationArtifactContext = ReplicationArtifactContext(task, it)
                    try {
                        // 检查版本
                        checkVersion(replicationArtifactContext)
                        // 准备同步详情信息
                        prepare(replicationInfo, replicationArtifactContext, replicationJobContext)
                        // 开始同步
                        startReplica(replicationArtifactContext, replicationJobContext)
                    } catch (exception: Exception) {
                        taskSuccess = false
                        // 记录异常
                        logger.error("replica to cluster:[${it.name}] failed, message: ${exception.message}")
                        // 任务执行失败
                        replicationJobContext.taskRecord.errorReason = exception.message
                        completeReplica(replicationJobContext, ReplicationStatus.FAILED)
                    }
                }
            }
            // 完成同步
            if (taskSuccess) {
                completeReplica(replicationJobContext, ReplicationStatus.SUCCESS)
            }
        } catch (exception: Exception) {
            // 记录异常
            replicationJobContext.taskRecord.errorReason = exception.message
            completeReplica(replicationJobContext, ReplicationStatus.FAILED)
        } finally {
            // 保存结果
            replicationJobContext.taskRecord.endTime = LocalDateTime.now()
            persistTask(replicationJobContext)
            logger.info("Replica task[$taskId] finished, task log: ${replicationJobContext.taskRecord}.")
        }
    }

    private fun checkProjectExists(context: ReplicationJobContext) {
        with(context) {
            val projectExists = repoDataService.checkProjectExists(task.localProjectId)
            if (!projectExists) {
                throw ErrorCodeException(ArtifactMessageCode.PROJECT_NOT_FOUND, task.localProjectId)
            }
        }
    }

    private fun checkVersion(context: ReplicationArtifactContext) {
        with(context) {
            val remoteVersion = artifactReplicaClient.version(authToken).data!!
            if (version != remoteVersion) {
                logger.warn("Local cluster's version[$version] is different from remote cluster[$remoteVersion].")
            }
        }
    }

    private fun prepare(
        replicationInfo: ReplicationInfo,
        context: ReplicationArtifactContext,
        jobContext: ReplicationJobContext
    ) {
        with(context) {
            val projectInfo = repoDataService.listProject(task.localProjectId).first()
            projectDetail =
                convertReplicationProject(jobContext, context, projectInfo, replicationInfo.repoInfo)
            jobContext.progress.totalProject = 1
            jobContext.progress.totalRepo += projectDetail.repoDetailList.size
        }
    }

    private fun completeReplica(context: ReplicationJobContext, status: ReplicationStatus) {
        if (context.isCronJob()) {
            context.status = ReplicationStatus.WAITING
        } else {
            context.status = status
        }
    }

    private fun persistTask(context: ReplicationJobContext) {
        context.task.status = context.status
        taskRepository.save(context.task)
        persistTaskLog(context)
    }

    private fun persistTaskLog(context: ReplicationJobContext) {
        context.taskRecord.status = context.status
        context.taskRecord.replicationProgress = context.progress
        taskLogRepository.save(context.taskRecord)
    }

    private fun persistTaskLogDetail(
        replicationTaskLogDetail: ReplicationTaskLogDetail
    ) {
        val taskLogDetail =
            with(replicationTaskLogDetail) {
                TReplicationTaskLogDetail(
                    taskLogKey = taskLogKey,
                    status = status,
                    masterName = masterName,
                    slaveName = slaveName,
                    projectId = projectId,
                    repoName = repoName,
                    packageName = packageName,
                    packageKey = packageKey,
                    type = type,
                    version = version,
                    failLevelArtifact = failLevelArtifact,
                    errorReason = errorReason
                )
            }
        taskLogDetailRepository.insert(taskLogDetail)
    }

    private fun convertReplicationProject(
        jobContext: ReplicationJobContext,
        context: ReplicationArtifactContext,
        localProjectInfo: ProjectInfo,
        repoInfoList: List<RepoInfo>
    ): ReplicationProjectDetail {
        return with(localProjectInfo) {
            val repoDetailList = mutableListOf<ReplicationRepoDetail>()
            repoInfoList.forEach {
                if (!repoDataService.checkRepoExists(this.name, it.localRepoName, it.type)) {
                    // 记录错误信息，跳过本次执行
                    val message =
                        "local type [${it.type}] repository [${this.name}/${it.localRepoName}] not found, " +
                            "replication to cluster [${context.currentClusterName}] failed."
                    logger.error(message)
                    val logDetail = ReplicationTaskLogDetail(
                        jobContext.taskLogKey,
                        ArtifactReplicationResult.FAILED,
                        masterName = jobContext.masterClusterNode.name,
                        slaveName = context.currentClusterName,
                        projectId = name,
                        repoName = it.localRepoName,
                        failLevelArtifact = ArtifactReplicationFailLevel.REPOSITORY,
                        errorReason = message
                    )
                    persistTaskLogDetail(logDetail)
                    return@forEach
                }
                val repoInfo = repoDataService.listRepository(this.name, it.localRepoName).first()
                val convertPackage = convertReplicationRepo(jobContext, context, repoInfo, it)
                repoDetailList.add(convertPackage)
            }

            ReplicationProjectDetail(
                localProjectInfo = this,
                repoDetailList = repoDetailList
            )
        }
    }

    private fun convertReplicationRepo(
        jobContext: ReplicationJobContext,
        context: ReplicationArtifactContext,
        localRepoInfo: RepositoryInfo,
        repoInfo: RepoInfo
    ): ReplicationRepoDetail {
        return with(localRepoInfo) {
            val packageDetailList = mutableListOf<ReplicationPackageDetail>()
            val repoDetail = repoDataService.getRepositoryDetail(projectId, name)
            repoInfo.packageInfo.forEach {
                if (!repoDataService.checkPackageVersionExists(this.projectId, this.name, it.packageKey, it.version)) {
                    // 记录错误信息，跳过本次执行
                    val message =
                        "local package [${it.name}] with version [${it.version}] repository " +
                            "[${this.projectId}/${this.name}] not found, " +
                            "replication to cluster [${context.currentClusterName}] failed."
                    logger.error(message)
                    val logDetail = ReplicationTaskLogDetail(
                        jobContext.taskLogKey,
                        ArtifactReplicationResult.FAILED,
                        masterName = jobContext.masterClusterNode.name,
                        slaveName = context.currentClusterName,
                        projectId = this.projectId,
                        repoName = this.name,
                        packageKey = it.packageKey,
                        packageName = it.name,
                        version = it.version,
                        failLevelArtifact = ArtifactReplicationFailLevel.PACKAGE,
                        errorReason = message
                    )
                    persistTaskLogDetail(logDetail)
                    return@forEach
                }
                packageDetailList.add(
                    ReplicationPackageDetail(
                        name = it.name,
                        packageKey = it.packageKey,
                        version = it.version
                    )
                )
            }
            ReplicationRepoDetail(
                localRepoDetail = repoDetail!!,
                packageDetailList = packageDetailList
            )
        }
    }

    private fun startReplica(context: ReplicationArtifactContext, jobContext: ReplicationJobContext) {
        checkInterrupted()
        with(context) {
            val localProjectId = projectDetail.localProjectInfo.name
            logger.info("Start to replica project [$localProjectId] to cluster: [$currentClusterName]")
            try {
                replicaProject(context, jobContext)
                logger.info("Success to replica project [$localProjectId] to cluster: [$currentClusterName].")
                jobContext.progress.successProject += 1
            } catch (interruptedException: InterruptedException) {
                throw interruptedException
            } catch (exception: RuntimeException) {
                jobContext.progress.failedProject += 1
                logger.error(
                    "Failed to replica project [$localProjectId] to cluster: [$currentClusterName].",
                    exception
                )
                val logDetail = ReplicationTaskLogDetail(
                    jobContext.taskLogKey,
                    ArtifactReplicationResult.FAILED,
                    masterName = jobContext.masterClusterNode.name,
                    slaveName = context.currentClusterName,
                    projectId = projectDetail.localProjectInfo.name,
                    failLevelArtifact = ArtifactReplicationFailLevel.PROJECT,
                    errorReason = exception.message
                )
                persistTaskLogDetail(logDetail)
            } finally {
                jobContext.progress.replicatedProject += 1
                persistTaskLog(jobContext)
            }
        }
    }

    private fun replicaProject(context: ReplicationArtifactContext, jobContext: ReplicationJobContext) {
        checkInterrupted()
        with(context) {
            // 创建项目
            val request = ProjectCreateRequest(
                name = projectDetail.localProjectInfo.name,
                displayName = projectDetail.localProjectInfo.displayName,
                description = projectDetail.localProjectInfo.description,
                operator = projectDetail.localProjectInfo.createdBy
            )
            replicationArtifactService.replicaProjectCreateRequest(context, request)
            // 同步仓库及制品
            this.projectDetail.repoDetailList.forEach {
                val localRepoKey = "${it.localRepoDetail.projectId}/${it.localRepoDetail.name}"
                logger.info("Start to replica repository [$localRepoKey] to cluster: [$currentClusterName]")
                try {
                    context.currentRepoDetail = it
                    replicaRepo(context, jobContext)
                    jobContext.progress.successRepo += 1
                    logger.info("Success to replica repository [$localRepoKey] to cluster: [$currentClusterName].")
                } catch (interruptedException: InterruptedException) {
                    throw interruptedException
                } catch (exception: RuntimeException) {
                    jobContext.progress.failedRepo += 1
                    logger.error(
                        "Failed to replica repository [$localRepoKey] to cluster: [$currentClusterName].",
                        exception
                    )
                    val logDetail = ReplicationTaskLogDetail(
                        jobContext.taskLogKey,
                        ArtifactReplicationResult.FAILED,
                        masterName = jobContext.masterClusterNode.name,
                        slaveName = context.currentClusterName,
                        projectId = projectDetail.localProjectInfo.name,
                        repoName = it.localRepoDetail.name,
                        failLevelArtifact = ArtifactReplicationFailLevel.REPOSITORY,
                        errorReason = exception.message
                    )
                    persistTaskLogDetail(logDetail)
                } finally {
                    jobContext.progress.replicatedRepo += 1
                    persistTaskLog(jobContext)
                }
            }
        }
    }

    private fun replicaRepo(context: ReplicationArtifactContext, jobContext: ReplicationJobContext) {
        checkInterrupted()
        with(context.currentRepoDetail) {
            // 创建仓库
            val replicaRequest = RepoCreateRequest(
                projectId = context.task.localProjectId,
                name = localRepoDetail.name,
                type = localRepoDetail.type,
                category = localRepoDetail.category,
                public = localRepoDetail.public,
                description = localRepoDetail.description,
                configuration = localRepoDetail.configuration,
                operator = localRepoDetail.createdBy
            )
            replicationArtifactService.replicaRepoCreateRequest(context, replicaRequest)
            // 同步包数据
            startReplicaPackage(context, jobContext)
        }
    }

    private fun startReplicaPackage(
        context: ReplicationArtifactContext,
        jobContext: ReplicationJobContext
    ) {
        with(context.currentRepoDetail) {
            val localProjectId = localRepoDetail.projectId
            val localRepoName = localRepoDetail.name
            packageDetailList.forEach {
                context.currentPackageDetail = it
                val packageSummary =
                    repoDataService.queryPackage(localProjectId, localRepoName, it.packageKey)
                // 同步包
                replicaPackage(packageSummary, context, jobContext)
            }
        }
    }

    private fun replicaPackage(
        packageSummary: PackageSummary,
        context: ReplicationArtifactContext,
        jobContext: ReplicationJobContext
    ) {
        checkInterrupted()
        val localProjectId = packageSummary.projectId
        val localRepoName = packageSummary.repoName
        val packageVersion =
            repoDataService.queryPackageVersion(
                localProjectId, localRepoName, packageSummary.key, context.currentPackageDetail.version
            )
        context.extMap = packageVersion.metadata
        val versionList = mutableListOf<String>()
        versionList.add(packageVersion.name)
        with(context) {
            val request =
                PackageVersionExistCheckRequest(localProjectId, localRepoName, packageSummary.key, versionList)
            val existPackageVersionList =
                artifactReplicaClient.checkPackageVersionExistList(authToken, request).data!!
            // 如果为空，说明该版本远程仓库中不存在，进行同步
            // 同步不存在的包版本
            replicaPackageVersion(
                packageSummary,
                packageVersion,
                context,
                jobContext,
                existPackageVersionList
            )
        }
    }

    private fun replicaPackageVersion(
        packageSummary: PackageSummary,
        packageVersion: PackageVersion,
        context: ReplicationArtifactContext,
        jobContext: ReplicationJobContext,
        existPackageVersionList: List<String>
    ) {
        checkInterrupted()
        with(context) {
            val packagePath =
                "${packageSummary.projectId}/${packageSummary.repoName}/${packageSummary.name}/${packageVersion.name}"
            // 包版本冲突检查
            if (existPackageVersionList.contains(packageVersion.name)) {
                when (task.setting.conflictStrategy) {
                    ConflictStrategy.SKIP -> {
                        if (logger.isDebugEnabled) {
                            logger.debug(
                                "Package version [$packagePath] to cluster: [$currentClusterName] conflict, skip it."
                            )
                        }
                        jobContext.progress.conflictedPackageVersion += 1
                        return
                    }
                    ConflictStrategy.OVERWRITE -> {
                        if (logger.isDebugEnabled) {
                            logger.debug("Package version [$packagePath] conflict, overwrite it.")
                        }
                    }
                    ConflictStrategy.FAST_FAIL -> throw IllegalArgumentException(
                        "Package version [$packagePath] conflict."
                    )
                }
            }
            try {
                // 查询元数据
                val metadata = if (task.setting.includeMetadata) {
                    packageVersion.metadata
                } else emptyMap()
                // 同步节点
                val replicaRequest = PackageVersionCreateRequest(
                    projectId = packageSummary.projectId,
                    repoName = packageSummary.repoName,
                    packageName = packageSummary.name,
                    packageKey = packageSummary.key,
                    packageType = packageSummary.type,
                    packageDescription = packageSummary.description,
                    versionName = packageVersion.name,
                    size = packageVersion.size,
                    manifestPath = null,
                    artifactPath = packageVersion.contentPath,
                    stageTag = packageVersion.stageTag,
                    metadata = metadata,
                    overwrite = true,
                    createdBy = packageVersion.createdBy
                )
                replicationArtifactService.replicaPackageVersionCreatedRequest(context, replicaRequest)
                jobContext.progress.successPackageVersion += 1
                logger.info("Success to replica package version [$packagePath].")
                // 同步节点
                startReplicaNode(context, jobContext)
                val logDetail = ReplicationTaskLogDetail(
                    jobContext.taskLogKey,
                    ArtifactReplicationResult.SUCCESS,
                    masterName = jobContext.masterClusterNode.name,
                    slaveName = context.currentClusterName,
                    projectId = packageSummary.projectId,
                    repoName = packageSummary.repoName,
                    packageName = packageSummary.name,
                    packageKey = packageSummary.key,
                    type = packageSummary.type,
                    version = packageVersion.name
                )
                persistTaskLogDetail(logDetail)
            } catch (interruptedException: InterruptedException) {
                throw interruptedException
            } catch (exception: RuntimeException) {
                jobContext.progress.failedPackageVersion += 1
                logger.error("Failed to replica package version [$packagePath].", exception)
                val logDetail = ReplicationTaskLogDetail(
                    jobContext.taskLogKey,
                    ArtifactReplicationResult.FAILED,
                    masterName = jobContext.masterClusterNode.name,
                    slaveName = context.currentClusterName,
                    projectId = packageSummary.projectId,
                    repoName = packageSummary.repoName,
                    packageName = packageSummary.name,
                    packageKey = packageSummary.key,
                    type = packageSummary.type,
                    version = packageVersion.name,
                    failLevelArtifact = ArtifactReplicationFailLevel.PACKAGE,
                    errorReason = exception.message
                )
                persistTaskLogDetail(logDetail)
            } finally {
                jobContext.progress.replicatedPackageVersion += 1
                if (jobContext.progress.replicatedPackageVersion % 50 == 0L) {
                    taskRepository.save(task)
                }
            }
        }
    }

    private fun startReplicaNode(
        context: ReplicationArtifactContext,
        jobContext: ReplicationJobContext
    ) {
        with(context) {
            val localProjectId = currentRepoDetail.localRepoDetail.projectId
            val localRepoName = currentRepoDetail.localRepoDetail.name
            val fullPathList = packageNodeMappingHandler.getMappingList(
                currentRepoDetail.localRepoDetail.type,
                currentPackageDetail.packageKey,
                currentPackageDetail.version,
                extMap
            )
            val fileNodeList = repoDataService.listFileNode(localProjectId, localRepoName, fullPathList)
            // fileNodeList.forEach { fullPathList.add(it.fullPath) }
            val request = NodeExistCheckRequest(localProjectId, localRepoName, fullPathList)
            val existFullPathList = artifactReplicaClient.checkNodeExistList(authToken, request).data!!
            // 同步不存在的节点
            fileNodeList.forEach { replicaNode(it, context, jobContext, existFullPathList) }
        }
    }

    private fun replicaNode(
        node: NodeInfo,
        context: ReplicationArtifactContext,
        jobContext: ReplicationJobContext,
        existFullPathList: List<String>
    ) {
        checkInterrupted()
        with(context) {
            val formattedNodePath = "${node.projectId}/${node.repoName}${node.fullPath}"
            // 节点冲突检查
            if (existFullPathList.contains(node.fullPath)) {
                when (task.setting.conflictStrategy) {
                    ConflictStrategy.SKIP -> {
                        if (logger.isDebugEnabled) {
                            logger.debug("File[$formattedNodePath] conflict, skip it.")
                        }
                        jobContext.progress.conflictedNode += 1
                        return
                    }
                    ConflictStrategy.OVERWRITE -> {
                        if (logger.isDebugEnabled) {
                            logger.debug("File[$formattedNodePath] conflict, overwrite it.")
                        }
                    }
                    ConflictStrategy.FAST_FAIL -> throw IllegalArgumentException("File[$formattedNodePath] conflict.")
                }
            }
            try {
                // 查询元数据
                val metadata = if (task.setting.includeMetadata) {
                    node.metadata
                } else emptyMap()
                // 同步节点
                val replicaRequest = NodeCreateRequest(
                    projectId = node.projectId,
                    repoName = node.repoName,
                    fullPath = node.fullPath,
                    folder = node.folder,
                    overwrite = true,
                    size = node.size,
                    sha256 = node.sha256!!,
                    md5 = node.md5!!,
                    metadata = metadata,
                    operator = node.createdBy
                )
                replicationArtifactService.replicaFile(context, replicaRequest)
                jobContext.progress.successNode += 1
                logger.info("Success to replica file [$formattedNodePath].")
            } catch (interruptedException: InterruptedException) {
                throw interruptedException
            } catch (exception: RuntimeException) {
                jobContext.progress.failedNode += 1
                logger.error("Failed to replica file [$formattedNodePath].", exception)
                val logDetail = ReplicationTaskLogDetail(
                    jobContext.taskLogKey,
                    ArtifactReplicationResult.FAILED,
                    masterName = jobContext.masterClusterNode.name,
                    slaveName = context.currentClusterName,
                    projectId = node.projectId,
                    repoName = node.repoName,
                    failLevelArtifact = ArtifactReplicationFailLevel.NODE,
                    errorReason = exception.message
                )
                persistTaskLogDetail(logDetail)
            } finally {
                jobContext.progress.replicatedNode += 1
                if (jobContext.progress.replicatedNode % 50 == 0L) {
                    taskRepository.save(task)
                }
            }
        }
    }

    private fun checkInterrupted() {
        if (Thread.interrupted()) {
            throw InterruptedException("Interrupted by user")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicationArtifactJobBean::class.java)
    }
}
