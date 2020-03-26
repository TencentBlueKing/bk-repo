package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.PermissionSet
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.replication.config.DEFAULT_VERSION
import com.tencent.bkrepo.replication.constant.TASK_ID_KEY
import com.tencent.bkrepo.replication.pojo.ReplicationProjectDetail
import com.tencent.bkrepo.replication.pojo.ReplicationRepoDetail
import com.tencent.bkrepo.replication.pojo.request.RoleReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.UserReplicaRequest
import com.tencent.bkrepo.replication.pojo.setting.ConflictStrategy
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import com.tencent.bkrepo.replication.repository.TaskRepository
import com.tencent.bkrepo.replication.service.NodeReplicationService
import com.tencent.bkrepo.replication.service.RepoDataService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.quartz.DisallowConcurrentExecution
import org.quartz.JobExecutionContext
import org.quartz.PersistJobDataAfterExecution
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.quartz.QuartzJobBean
import java.time.Duration
import java.time.LocalDateTime

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
class FullReplicationJob : QuartzJobBean() {

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var repoDataService: RepoDataService

    @Autowired
    private lateinit var nodeReplicationService: NodeReplicationService

    @Value("\${spring.application.version}")
    private var version: String = DEFAULT_VERSION

    override fun executeInternal(context: JobExecutionContext) {
        val taskId = context.jobDetail.jobDataMap.getString(TASK_ID_KEY)
        logger.info("Start to execute replication task[$taskId].")
        val task = taskRepository.findByIdOrNull(taskId) ?: run {
            logger.error("Task[$taskId] does not exist.")
            return
        }

        try {
            with(task.setting) {
                val replicaContext = ReplicaJobContext(task)
                // 更新状态
                task.status = ReplicationStatus.REPLICATING
                task.startTime = LocalDateTime.now()
                // 检查版本
                checkVersion(replicaContext)
                // 准备同步详情信息
                prepare(replicaContext)
                // 更新task
                taskRepository.save(task)
                // 开始同步
                replica(replicaContext)
                // 更新状态
                task.status = ReplicationStatus.SUCCESS
            }
        } catch (exception: Exception) {
            // 记录异常
            task.status = ReplicationStatus.FAILED
            task.errorReason = exception.message
        } finally {
            // 保存结果
            task.endTime = LocalDateTime.now()
            taskRepository.save(task)
            val consumeSeconds = Duration.between(task.startTime!!, task.endTime!!).seconds
            logger.info("Replica task[$taskId] is finished[${task.status}], reason[${task.errorReason}], consume [$consumeSeconds]s.")
        }
    }

    private fun checkVersion(context: ReplicaJobContext) {
        with(context) {
            val remoteVersion = dataReplicationService.version(authToken).data!!
            if (version != remoteVersion) {
                logger.warn("The local cluster's version[$version] is different from remote cluster's version[$remoteVersion]")
            }
        }
    }

    private fun prepare(context: ReplicaJobContext) {
        with(context) {
            projectDetailList = repoDataService.listProject(task.localProjectId).map {
                convertReplicationProject(it, task.remoteProjectId, task.remoteRepoName)
            }
            task.replicationProgress.totalProject = projectDetailList.size
            projectDetailList.forEach { project ->
                task.replicationProgress.totalRepo += project.repoDetailList.size
                project.repoDetailList.forEach { repo -> task.replicationProgress.totalNode += repo.fileCount }
            }

        }
    }

    private fun replica(context: ReplicaJobContext) {
        context.projectDetailList.forEach {
            try {
                context.currentProjectDetail = it
                replicaProject(context)
                context.task.replicationProgress.successProject += 1
            } catch (exception: Exception) {
                context.task.replicationProgress.failedProject += 1
                logger.error("Replica project[$it] failed.", exception)
            } finally {
                context.task.replicationProgress.replicatedProject += 1
                taskRepository.save(context.task)
            }
        }
    }

    private fun replicaProject(context: ReplicaJobContext) {
        with(context.currentProjectDetail) {
                // 创建项目
                createProject(context)
                // 同步权限
                replicaUserAndPermission(context)
                // 同步仓库
                this.repoDetailList.forEach {
                    try {
                        context.currentRepoDetail = it
                        replicaRepo(context)
                        context.task.replicationProgress.successRepo += 1
                    } catch (exception: Exception) {
                        context.task.replicationProgress.failedRepo += 1
                        logger.error("Replica repository[$it] failed.", exception)
                    } finally {
                        context.task.replicationProgress.replicatedRepo += 1
                        taskRepository.save(context.task)
                    }
                }
        }
    }

    private fun replicaRepo(context: ReplicaJobContext) {
        with(context.currentRepoDetail) {
            // 创建仓库
            createRepo(context)
            // 同步权限
            replicaUserAndPermission(context, true)
            // 同步节点
            var page = 0
            var fileNodeList = repoDataService.listFileNode(localRepoInfo.projectId, localRepoInfo.name, "/", page, pageSize)
            while (fileNodeList.isNotEmpty()) {
                fileNodeList.forEach {
                    replicaNode(it, context)
                }
                page += 1
                fileNodeList = repoDataService.listFileNode(localRepoInfo.projectId, localRepoInfo.name, "/", page, pageSize)
            }
        }
    }

    private fun replicaNode(node: NodeInfo, context: ReplicaJobContext) {
        with(context) {
            val remoteProjectId = context.currentProjectDetail.remoteProjectId
            val remoteRepoName = context.currentRepoDetail.remoteRepoName
            // 节点冲突检查
            if (dataReplicationService.checkNodeExist(authToken, remoteProjectId, remoteRepoName, node.fullPath).data == true) {
                when (task.setting.conflictStrategy) {
                    ConflictStrategy.SKIP -> {
                        logger.warn("Node[$node] conflict, skip it.")
                        task.replicationProgress.conflictedNode += 1
                        return
                    }
                    ConflictStrategy.OVERWRITE -> {
                        logger.warn("Node[$node] conflict, overwrite it.")
                    }
                    ConflictStrategy.FAST_FAIL -> throw RuntimeException("Node[$node] conflict.")
                }
            }
            // 同步节点
            try {
                // 查询元数据
                val metadata = if (task.setting.includeMetadata) {
                    repoDataService.getMetadata(node)
                } else null
                // 查询文件
                val file = repoDataService.getFile(node, currentRepoDetail.localRepoInfo)
                // 同步节点
                nodeReplicationService.replicaNode(context, file, node, metadata)
                task.replicationProgress.successNode += 1
            } catch (exception: Exception) {
                logger.error("Replica node[$node] failed.", exception)
                task.replicationProgress.failedNode += 1
            } finally {
                task.replicationProgress.replicatedNode += 1
                if (task.replicationProgress.replicatedNode % 10 == 0L) {
                    taskRepository.save(task)
                }
            }
        }
    }

    private fun replicaUserAndPermission(context: ReplicaJobContext, isRepo: Boolean = false) {
        with(context) {
            if (task.setting.includePermission) {
                return
            }
            val remoteProjectId = currentProjectDetail.remoteProjectId
            val remoteRepoName = if (isRepo) currentRepoDetail.remoteRepoName else null
            val localProjectId = currentProjectDetail.localProjectInfo.name
            val localRepoName = if (isRepo) currentRepoDetail.localRepoInfo.name else null

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
                val userIdList = localUserList.filter { user -> user.roles.contains(role.id) }.map { user -> user.userId }
                // 创建角色-用户绑定关系
                createUserRoleRelationShip(this, remoteRoleId, userIdList)
            }
            // 同步权限数据
            val resourceType = if (isRepo) ResourceType.REPO else ResourceType.PROJECT
            val localPermissionList = repoDataService.listPermission(resourceType, remoteProjectId, remoteRepoName)
            val remotePermissionList = dataReplicationService.listPermission(authToken, resourceType, localProjectId, localRepoName).data!!

            localPermissionList.forEach { permission ->
                // 过滤已存在的权限
                if (!containsPermission(permission, remotePermissionList)) {
                    // 创建用户
                    permission.users.forEach {
                        if (!traversedUserList.contains(it.id)) {
                            createUser(this, it.id)
                        }
                    }
                    createPermission(this, permission, roleIdMap)
                }
            }
        }
    }

    private fun createProject(context: ReplicaJobContext) {
        with(context) {
            val request = ProjectCreateRequest(
                name = currentProjectDetail.remoteProjectId,
                displayName = currentProjectDetail.localProjectInfo.displayName,
                description = currentProjectDetail.localProjectInfo.description
            )
            dataReplicationService.replicaProject(authToken, request)
        }
    }

    private fun createRepo(context: ReplicaJobContext) {
        with(context) {
            val request = RepoCreateRequest(
                projectId = currentProjectDetail.remoteProjectId,
                name = currentRepoDetail.remoteRepoName,
                type = currentRepoDetail.localRepoInfo.type,
                category = currentRepoDetail.localRepoInfo.category,
                public = currentRepoDetail.localRepoInfo.public,
                description = currentRepoDetail.localRepoInfo.description,
                // 不同步仓库配置
                configuration = LocalConfiguration()
            )
            dataReplicationService.replicaRepo(authToken, request)
        }
    }

    private fun createUser(context: ReplicaJobContext, user: User) {
        with(context) {
            val request = UserReplicaRequest(
                userId = user.userId,
                name = user.name,
                pwd = user.pwd!!,
                admin = user.admin,
                tokens = user.tokens
            )
            dataReplicationService.replicaUser(authToken, request).data!!
        }
    }

    private fun createUser(context: ReplicaJobContext, uid: String) {
        with(context) {
            val userInfo = repoDataService.getUserDetail(uid)!!
            createUser(this, userInfo)
        }
    }

    private fun createRole(context: ReplicaJobContext, role: Role, projectId: String, repoName: String? = null): String {
        with(context) {
            val request = RoleReplicaRequest(
                roleId = role.roleId,
                name = role.name,
                type = role.type,
                projectId = projectId,
                repoName = repoName,
                admin = role.admin
            )
            return dataReplicationService.replicaRole(authToken, request).data!!.roleId
        }
    }

    private fun createUserRoleRelationShip(context: ReplicaJobContext, remoteRoleId: String, userIdList: List<String>) {
        with(context) {
            dataReplicationService.replicaUserRoleRelationShip(authToken, remoteRoleId, userIdList)
        }
    }

    private fun createPermission(context: ReplicaJobContext, permission: Permission, roleIdMap: Map<String, String>) {
        with(context) {
            // 查询角色对应id
            val roles = permission.roles.map {
                PermissionSet(roleIdMap[it.id] ?: error("Can not find corresponding role id[${it.id}]."), it.action)
            }
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
            dataReplicationService.replicaPermission(authToken, request)
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
            val fileCount = repoDataService.countFileNode(this)
            ReplicationRepoDetail(
                localRepoInfo = this,
                fileCount = fileCount,
                remoteRepoName = remoteRepoName ?: this.name
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FullReplicationJob::class.java)
        private const val pageSize = 500
    }
}
