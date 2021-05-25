/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.auth.pojo.permission.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.Permission
import com.tencent.bkrepo.auth.pojo.role.Role
import com.tencent.bkrepo.auth.pojo.user.User
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.job.replicator.ArtifactReplicator
import com.tencent.bkrepo.replication.job.replicator.BlobReplicator
import com.tencent.bkrepo.replication.job.replicator.Replicator
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.ReplicationProjectDetail
import com.tencent.bkrepo.replication.pojo.ReplicationRepoDetail
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeName
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeType
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.record.request.RecordDetailInitialRequest
import com.tencent.bkrepo.replication.pojo.request.RoleReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.UserReplicaRequest
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.setting.ConflictStrategy
import com.tencent.bkrepo.replication.schedule.ReplicaTaskScheduler
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.replication.service.ReplicationService
import com.tencent.bkrepo.replication.service.RepoDataService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 调度类型同步任务逻辑实现类
 * 任务由线程池执行
 */
@Suppress("TooGenericExceptionCaught")
@Component
class ScheduledReplicaJobBean(
    private val clusterNodeService: ClusterNodeService,
    private val replicaTaskService: ReplicaTaskService,
    private val replicaRecordService: ReplicaRecordService,
    private val repoDataService: RepoDataService,
    private val replicationService: ReplicationService,
    private val replicaTaskScheduler: ReplicaTaskScheduler
) {
    private val threadPoolExecutor: ThreadPoolExecutor = buildThreadPoolExecutor()

    /**
     * 执行同步任务
     * @param taskId 任务id
     * 该任务只能由一个节点执行，已经成功抢占到锁才能执行到此处
     */
    fun execute(taskId: String) {
        logger.info("Start to execute replication task[$taskId].")
        val task = findAndCheckTask(taskId) ?: return
        try {
            // 查询同步对象
            val taskDetail = replicaTaskService.getDetailByTaskKey(taskId)
            // 创建同步记录
            val taskRecord = replicaRecordService.initialRecord(taskDetail.task.key)
            task.remoteClusters.map { submit(taskDetail, taskRecord, it) }.forEach {
                it.get()
            }

        } catch (exception: Exception) {
            // 记录异常
            replicationContext.taskRecord.errorReason = exception.message
            completeReplica(replicationContext, ReplicationStatus.FAILED)
        } finally {
            // 保存结果
            replicationContext.taskRecord.endTime = LocalDateTime.now()
            completeReplica(replicationContext)
            logger.info("Replica task[$taskId] finished, task log: ${replicationContext.taskRecord}.")
        }
    }

    /**
     * 提交任务到线程池执行
     * @param taskDetail 任务详情
     * @param taskRecord 执行记录
     * @param clusterNodeName 远程集群
     */
    private fun submit(
        taskDetail: ReplicaTaskDetail,
        taskRecord: ReplicaRecordInfo,
        clusterNodeName: ClusterNodeName
    ): Future<ExecutionResult> {
        return threadPoolExecutor.submit<ExecutionResult> {
            try {
                val clusterNode = clusterNodeService.getByClusterId(clusterNodeName.id)
                require(clusterNode != null) { "Cluster[${clusterNodeName.id}] does not exist." }
                taskDetail.objects.map { taskObject ->
                    // 初始化record detail
                    val context = initialContext(taskDetail, taskObject, taskRecord, clusterNode)
                    val scheduledReplicator = chooseReplicator(context)
                    scheduledReplicator.replica(context)
                }
                ExecutionResult(status = ExecutionStatus.SUCCESS)
            } catch (exception: Throwable) {
                ExecutionResult(status = ExecutionStatus.FAILED, errorReason = exception.message.orEmpty())
            }
        }
    }

    /**
     * 根据context选择合适的数据同步类
     */
    private fun chooseReplicator(context: ReplicaContext): Replicator {
        return when(context.clusterNodeInfo.type) {
            ClusterNodeType.STANDALONE -> SpringContextUtils.getBean<ArtifactReplicator>()
            ClusterNodeType.EDGE -> SpringContextUtils.getBean<BlobReplicator>()
            else -> throw UnsupportedOperationException()
        }
    }

    /**
     * 初始化执行记录详情
     */
    private fun initialContext(
        taskDetail: ReplicaTaskDetail,
        taskObject: ReplicaObjectInfo,
        taskRecord: ReplicaRecordInfo,
        clusterNodeInfo: ClusterNodeInfo
    ): ReplicaContext {
        val request = RecordDetailInitialRequest(
            recordId = taskRecord.id,
            localCluster = taskDetail.task.projectId,
            remoteCluster = clusterNodeInfo.name
        )
        val detail = replicaRecordService.initialRecordDetail(request)
        return ReplicaContext(taskDetail, taskObject, clusterNodeInfo)
    }

    /**
     * 查找并检查任务状态
     * @return 如果任务不存在或不能被执行，返回null，否则返回任务信息
     */
    private fun findAndCheckTask(taskId: String): TReplicaTask? {
        // 任务不存在，删除任务
        val task = replicaTaskDao.findById(taskId) ?: run {
            logger.warn("Task[$taskId] does not exist, delete job and trigger.")
            replicaTaskScheduler.deleteJob(taskId)
            return null
        }
        // 任务未开启，跳过
        if (!task.enabled) {
            logger.info("Task[$taskId] status is paused, ignore executing.")
            return null
        }
        // 任务正在执行，跳过
        if (task.lastExecutionStatus == ExecutionStatus.RUNNING) {
            logger.info("Task[$taskId] status is running, ignore executing.")
            return null
        }
        return task
    }

    /**
     * 创建线程池
     */
    private fun buildThreadPoolExecutor(): ThreadPoolExecutor {
        val namedThreadFactory = ThreadFactoryBuilder().setNameFormat("replica-worker-%d").build()
        return ThreadPoolExecutor(100, 500, 30, TimeUnit.SECONDS,
            ArrayBlockingQueue(10), namedThreadFactory, ThreadPoolExecutor.AbortPolicy())
    }

    private fun startReplica(context: ReplicaContext) {
        checkInterrupted()
        context.projectDetailList.forEach {
            val localProjectId = it.localProjectInfo.name
            val remoteProjectId = it.remoteProjectId
            val repoCount = it.repoDetailList.size
            logger.info("Start to replica project [$localProjectId] to [$remoteProjectId], repo count: $repoCount.")
            try {
                context.currentProjectDetail = it
                context.remoteProjectId = it.remoteProjectId
                replicaProject(context)
                logger.info("Success to replica project [$localProjectId] to [$remoteProjectId].")
                context.progress.successProject += 1
            } catch (interruptedException: InterruptedException) {
                throw interruptedException
            } catch (exception: RuntimeException) {
                context.progress.failedProject += 1
                logger.error("Failed to replica project [$localProjectId] to [$remoteProjectId].", exception)
            } finally {
                context.progress.replicatedProject += 1
                persistTaskLog(context)
            }
        }
    }

    private fun replicaProject(context: ReplicaContext) {
        checkInterrupted()
        with(context.currentProjectDetail) {
            // 创建项目
            val request = ProjectCreateRequest(
                name = remoteProjectId,
                displayName = localProjectInfo.displayName,
                description = localProjectInfo.description,
                operator = localProjectInfo.createdBy
            )
            replicationService.replicaProjectCreateRequest(context, request)
            // 同步权限
            replicaUserAndPermission(context)
            // 同步仓库
            this.repoDetailList.forEach {
                val localRepoKey = "${it.localRepoDetail.projectId}/${it.localRepoDetail.name}"
                val remoteRepoKey = "${context.remoteProjectId}/${it.remoteRepoName}"
                val fileCount = it.fileCount
                logger.info("Start to replica repository [$localRepoKey] to [$remoteRepoKey], file count: $fileCount.")
                try {
                    context.currentRepoDetail = it
                    context.remoteRepoName = it.remoteRepoName
                    replicaRepo(context)
                    context.progress.successRepo += 1
                    logger.info("Success to replica repository [$localRepoKey] to [$remoteRepoKey].")
                } catch (interruptedException: InterruptedException) {
                    throw interruptedException
                } catch (exception: RuntimeException) {
                    context.progress.failedRepo += 1
                    logger.error(
                        "Failed to replica repository [$localRepoKey] to [$remoteRepoKey].",
                        exception
                    )
                } finally {
                    context.progress.replicatedRepo += 1
                    persistTaskLog(context)
                }
            }
        }
    }

    private fun replicaRepo(context: ReplicaContext) {
        checkInterrupted()
        with(context.currentRepoDetail) {
            // 创建仓库
            val replicaRequest = RepoCreateRequest(
                projectId = context.remoteProjectId,
                name = context.remoteRepoName,
                type = localRepoDetail.type,
                category = localRepoDetail.category,
                public = localRepoDetail.public,
                description = localRepoDetail.description,
                configuration = localRepoDetail.configuration,
                operator = localRepoDetail.createdBy
            )
            replicationService.replicaRepoCreateRequest(context, replicaRequest)
            // 同步权限
            replicaUserAndPermission(context, true)
            // 同步节点
            var page = 1
            val localProjectId = localRepoDetail.projectId
            val localRepoName = localRepoDetail.name
            var fileNodeList = repoDataService.listFileNode(localProjectId, localRepoName, ROOT, page, pageSize)
            while (fileNodeList.isNotEmpty()) {
                val fullPathList = fileNodeList.map { it.fullPath }
                val request = NodeExistCheckRequest(localProjectId, localRepoName, fullPathList)
                val existFullPathList =
                    context.artifactReplicaClient.checkNodeExistList(context.authToken, request).data!!
                // 同步不存在的节点
                fileNodeList.forEach { replicaNode(it, context, existFullPathList) }
                page += 1
                fileNodeList = repoDataService.listFileNode(localProjectId, localRepoName, ROOT, page, pageSize)
            }
        }
    }

    private fun replicaNode(node: NodeInfo, context: ReplicaContext, existFullPathList: List<String>) {
        checkInterrupted()
        with(context) {
            val formattedNodePath = "${node.projectId}/${node.repoName}${node.fullPath}"
            // 节点冲突检查
            if (existFullPathList.contains(node.fullPath)) {
                when (task.setting.conflictStrategy) {
                    ConflictStrategy.SKIP -> {
                        logger.debug("File[$formattedNodePath] conflict, skip it.")
                        progress.conflictedNode += 1
                        return
                    }
                    ConflictStrategy.OVERWRITE -> {
                        logger.debug("File[$formattedNodePath] conflict, overwrite it.")
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
                    projectId = remoteProjectId,
                    repoName = remoteRepoName,
                    fullPath = node.fullPath,
                    folder = node.folder,
                    overwrite = true,
                    size = node.size,
                    sha256 = node.sha256!!,
                    md5 = node.md5!!,
                    metadata = metadata,
                    operator = node.createdBy
                )
                replicationService.replicaFile(context, replicaRequest)
                progress.successNode += 1
                logger.info("Success to replica file [$formattedNodePath].")
            } catch (interruptedException: InterruptedException) {
                throw interruptedException
            } catch (exception: RuntimeException) {
                progress.failedNode += 1
                logger.error("Failed to replica file [$formattedNodePath].", exception)
            } finally {
                progress.replicatedNode += 1
                if (progress.replicatedNode % 50 == 0L) {
                    taskRepository.save(task)
                }
            }
        }
    }

    private fun replicaUserAndPermission(context: ReplicaContext, isRepo: Boolean = false) {
        with(context) {
            if (task.setting.includePermission) {
                return
            }
            val remoteProjectId = remoteProjectId
            val remoteRepoName = if (isRepo) remoteRepoName else null
            val localProjectId = currentProjectDetail.localProjectInfo.name
            val localRepoName = if (isRepo) currentRepoDetail.localRepoDetail.name else null

            // 查询所有相关联的角色, 该步骤可以查询到所有角色
            val localRoleList = repoDataService.listRole(localProjectId, localRepoName)
            // 查询所有角色所关联的用户，该步骤不能查询到所有用户
            val localUserList = repoDataService.listUser(localRoleList.map { it.id!! })
            // 记录已经遍历过的用户
            val traversedUserList = mutableListOf<String>()
            // 集群间的roleId映射关系
            val roleIdMap = mutableMapOf<String, String>()
            localUserList.forEach {
                // 创建用户
                createUser(this, it)
                traversedUserList.add(it.userId)
            }
            localRoleList.forEach { role ->
                // 创建角色
                val remoteRoleId = createRole(this, role, remoteProjectId, remoteRepoName)
                roleIdMap[role.roleId] = remoteRoleId
                // 包含该角色的用户列表
                val userIdList =
                    localUserList.filter { user -> user.roles.contains(role.id) }.map { user -> user.userId }
                // 创建角色-用户绑定关系
                createUserRoleRelationShip(this, remoteRoleId, userIdList)
            }
            // 同步权限数据
            val localPermissionList = repoDataService.listPermission(remoteProjectId, remoteRepoName)
            val remotePermissionList =
                artifactReplicaClient.listPermission(authToken, localProjectId, localRepoName).data!!

            localPermissionList.forEach { permission ->
                // 过滤已存在的权限
                if (containsPermission(permission, remotePermissionList)) {
                    return@forEach
                }
                // 创建用户
                permission.users.filter { !traversedUserList.contains(it) }.forEach {
                    createUser(this, it)
                }
                createPermission(this, permission, roleIdMap)
            }
        }
    }

    private fun createUser(context: ReplicaContext, user: User) {
        with(context) {
            val request = UserReplicaRequest(
                userId = user.userId,
                name = user.name,
                pwd = user.pwd!!,
                admin = user.admin,
                tokens = user.tokens
            )
            artifactReplicaClient.replicaUser(authToken, request).data!!
        }
    }

    private fun createUser(context: ReplicaContext, uid: String) {
        with(context) {
            val userInfo = repoDataService.getUserDetail(uid)!!
            createUser(this, userInfo)
        }
    }

    private fun createRole(
        context: ReplicaContext,
        role: Role,
        projectId: String,
        repoName: String? = null
    ): String {
        with(context) {
            val request = RoleReplicaRequest(
                roleId = role.roleId,
                name = role.name,
                type = role.type,
                projectId = projectId,
                repoName = repoName,
                admin = role.admin
            )
            return artifactReplicaClient.replicaRole(authToken, request).data!!.roleId
        }
    }

    private fun createUserRoleRelationShip(
        context: ReplicaContext,
        remoteRoleId: String,
        userIdList: List<String>
    ) {
        with(context) {
            artifactReplicaClient.replicaUserRoleRelationShip(authToken, remoteRoleId, userIdList)
        }
    }

    private fun createPermission(context: ReplicaContext, permission: Permission, roleIdMap: Map<String, String>) {
        with(context) {
            // 查询角色对应id
            val roles = permission.roles
            // 创建权限
            val request = CreatePermissionRequest(
                resourceType = permission.resourceType,
                projectId = permission.projectId,
                permName = permission.permName,
                repos = permission.repos,
                includePattern = permission.includePattern,
                excludePattern = permission.excludePattern,
                users = permission.users,
                roles = roles,
                createBy = SYSTEM_USER,
                updatedBy = SYSTEM_USER
            )
            artifactReplicaClient.replicaPermission(authToken, request)
        }
    }

    private fun containsPermission(permission: Permission, permissionList: List<Permission>): Boolean {
        permissionList.forEach {
            if (it.projectId == permission.projectId && it.permName == permission.permName) {
                return true
            }
        }
        return false
    }

    private fun convertReplicationProject(
        localProjectInfo: ProjectInfo,
        localRepoName: String? = null,
        remoteProjectId: String? = null,
        remoteRepoName: String? = null
    ): ReplicationProjectDetail {
        return with(localProjectInfo) {
            val repoDetailList = repoDataService.listRepository(this.name, localRepoName).map {
                convertReplicationRepo(it, remoteRepoName)
            }
            ReplicationProjectDetail(
                localProjectInfo = this,
                remoteProjectId = remoteProjectId ?: this.name,
                repoDetailList = repoDetailList
            )
        }
    }

    private fun convertReplicationRepo(
        localRepoInfo: RepositoryInfo,
        remoteRepoName: String? = null
    ): ReplicationRepoDetail {
        return with(localRepoInfo) {
            val repoDetail = repoDataService.getRepositoryDetail(projectId, name)
            val fileCount = repoDataService.countFileNode(this)
            ReplicationRepoDetail(
                localRepoDetail = repoDetail!!,
                fileCount = fileCount,
                remoteRepoName = remoteRepoName ?: this.name
            )
        }
    }

    private fun checkInterrupted() {
        if (Thread.interrupted()) {
            throw InterruptedException("Interrupted by user")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScheduledReplicaJobBean::class.java)
        private const val pageSize = 500
    }
}

/**
 * 执行结果
 */
data class ExecutionResult(
    val status: ExecutionStatus,
    val errorReason: String? = null
)

