package com.tencent.bkrepo.replication.handler.full

import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.PermissionSet
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.replication.config.DEFAULT_VERSION
import com.tencent.bkrepo.replication.handler.AbstractHandler
import com.tencent.bkrepo.replication.job.ReplicationContext
import com.tencent.bkrepo.replication.model.TReplicaTriggers
import com.tencent.bkrepo.replication.pojo.ReplicationProjectDetail
import com.tencent.bkrepo.replication.pojo.request.NodeExistCheckRequest
import com.tencent.bkrepo.replication.pojo.request.RoleReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.UserReplicaRequest
import com.tencent.bkrepo.replication.pojo.setting.ConflictStrategy
import com.tencent.bkrepo.replication.repository.TaskRepository
import com.tencent.bkrepo.replication.service.ReplicationService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.quartz.Trigger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update

class FullJobHandler : AbstractHandler() {

    @Value("\${spring.application.version}")
    private var version: String = DEFAULT_VERSION

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var replicationService: ReplicationService

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    fun updateTriggerStatus(keyName: String, keyGroup: String) {
        val query = Query()
        val update = Update()
        query.addCriteria(
            Criteria.where(TReplicaTriggers::keyName.name).`is`(keyName).and(
                TReplicaTriggers::keyGroup.name
            ).`is`(keyGroup)
        )
        update.set(TReplicaTriggers::state.name, Trigger.TriggerState.NORMAL.name)
        mongoTemplate.updateFirst(query, update, TReplicaTriggers::class.java)
    }

    fun checkVersion(context: ReplicationContext) {
        with(context) {
            val remoteVersion = replicationClient.version(authToken).data!!
            if (version != remoteVersion) {
                logger.warn("The local cluster's version[$version] is different from remote cluster's version[$remoteVersion]")
            }
        }
    }

    fun prepare(context: ReplicationContext) {
        with(context) {
            projectDetailList = repoDataService.listProject(task.localProjectId).map {
                convertReplicationProject(it, task.localRepoName, task.remoteProjectId, task.remoteRepoName)
            }
            task.replicationProgress.totalProject = projectDetailList.size
            projectDetailList.forEach { project ->
                task.replicationProgress.totalRepo += project.repoDetailList.size
                project.repoDetailList.forEach { repo -> task.replicationProgress.totalNode += repo.fileCount }
            }
        }
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

    fun startReplica(context: ReplicationContext) {
        context.projectDetailList.forEach {
            try {
                context.currentProjectDetail = it
                context.remoteProjectId = it.remoteProjectId
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

    private fun replicaProject(context: ReplicationContext) {
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
                try {
                    context.currentRepoDetail = it
                    context.remoteRepoName = it.remoteRepoName
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

    private fun replicaRepo(context: ReplicationContext) {
        with(context.currentRepoDetail) {
            // 创建仓库
            val replicaRequest = RepoCreateRequest(
                projectId = context.remoteProjectId,
                name = context.remoteRepoName,
                type = localRepoInfo.type,
                category = localRepoInfo.category,
                public = localRepoInfo.public,
                description = localRepoInfo.description,
                configuration = localRepoInfo.configuration,
                operator = localRepoInfo.createdBy
            )
            replicationService.replicaRepoCreateRequest(context, replicaRequest)
            // 同步权限
            replicaUserAndPermission(context, true)
            // 同步节点
            var page = 0
            var fileNodeList = repoDataService.listFileNode(localRepoInfo.projectId, localRepoInfo.name, StringPool.ROOT, page, pageSize)
            while (fileNodeList.isNotEmpty()) {
                var fullPathList = mutableListOf<String>()
                fileNodeList.forEach { fullPathList.add(it.fullPath) }
                with(context) {
                    val nodeCheckRequest = NodeExistCheckRequest(localRepoInfo.projectId, localRepoInfo.name, fullPathList)
                    val existFullPathList = replicationClient.checkNodeExistList(authToken, nodeCheckRequest).data!!
                    logger.debug("node path list params [$nodeCheckRequest], result [$existFullPathList]")
                    // 同步不存在的节点
                    fileNodeList.forEach { replicaNode(it, context, existFullPathList) }
                }

                page += 1
                fileNodeList = repoDataService.listFileNode(localRepoInfo.projectId, localRepoInfo.name, StringPool.ROOT, page, pageSize)
            }
        }
    }

    private fun replicaNode(node: NodeInfo, context: ReplicationContext, existFullPathList: List<String>) {
        with(context) {
            // 节点冲突检查
            if (existFullPathList.contains(node.fullPath)) {
                when (task.setting.conflictStrategy) {
                    ConflictStrategy.SKIP -> {
                        logger.debug("Node[$node] conflict, skip it.")
                        task.replicationProgress.conflictedNode += 1
                        return
                    }
                    ConflictStrategy.OVERWRITE -> {
                        logger.debug("Node[$node] conflict, overwrite it.")
                    }
                    ConflictStrategy.FAST_FAIL -> throw RuntimeException("Node[$node] conflict.")
                }
            }
            try {
                // 查询元数据
                val metadata = if (task.setting.includeMetadata) {
                    repoDataService.getMetadata(node)
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
                logger.info("start to replica file ${replicaRequest.projectId} ,${replicaRequest.repoName}, ${replicaRequest.fullPath}")
                replicationService.replicaFile(context, replicaRequest)
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

    private fun replicaUserAndPermission(context: ReplicationContext, isRepo: Boolean = false) {
        with(context) {
            if (task.setting.includePermission) {
                return
            }
            val remoteProjectId = remoteProjectId
            val remoteRepoName = if (isRepo) remoteRepoName else null
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
                val userIdList =
                    localUserList.filter { user -> user.roles.contains(role.id) }.map { user -> user.userId }
                // 创建角色-用户绑定关系
                createUserRoleRelationShip(this, remoteRoleId, userIdList)
            }
            // 同步权限数据
            val resourceType = if (isRepo) ResourceType.REPO else ResourceType.PROJECT
            val localPermissionList = repoDataService.listPermission(resourceType, remoteProjectId, remoteRepoName)
            val remotePermissionList =
                replicationClient.listPermission(authToken, resourceType, localProjectId, localRepoName).data!!

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

    private fun createUser(context: ReplicationContext, user: User) {
        with(context) {
            val request = UserReplicaRequest(
                userId = user.userId,
                name = user.name,
                pwd = user.pwd!!,
                admin = user.admin,
                tokens = user.tokens
            )
            replicationClient.replicaUser(authToken, request).data!!
        }
    }

    private fun createUser(context: ReplicationContext, uid: String) {
        with(context) {
            val userInfo = repoDataService.getUserDetail(uid)!!
            createUser(this, userInfo)
        }
    }

    private fun createRole(context: ReplicationContext, role: Role, projectId: String, repoName: String? = null): String {
        with(context) {
            val request = RoleReplicaRequest(
                roleId = role.roleId,
                name = role.name,
                type = role.type,
                projectId = projectId,
                repoName = repoName,
                admin = role.admin
            )
            return replicationClient.replicaRole(authToken, request).data!!.roleId
        }
    }

    private fun createUserRoleRelationShip(context: ReplicationContext, remoteRoleId: String, userIdList: List<String>) {
        with(context) {
            replicationClient.replicaUserRoleRelationShip(authToken, remoteRoleId, userIdList)
        }
    }

    private fun createPermission(context: ReplicationContext, permission: Permission, roleIdMap: Map<String, String>) {
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
            replicationClient.replicaPermission(authToken, request)
        }
    }

    fun containsPermission(permission: Permission, permissionList: List<Permission>): Boolean {
        permissionList.forEach {
            if (it.projectId == permission.projectId && it.permName == permission.permName) {
                return true
            }
        }
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FullJobHandler::class.java)
        private const val pageSize = 500
    }
}
