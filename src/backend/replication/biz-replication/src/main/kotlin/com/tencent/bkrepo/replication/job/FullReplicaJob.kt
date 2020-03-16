package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.api.ServiceRoleResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.PermissionSet
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.replication.api.ReplicaResource
import com.tencent.bkrepo.replication.config.FeignClientFactory
import com.tencent.bkrepo.replication.constant.TASK_ID_KEY
import com.tencent.bkrepo.replication.pojo.ConflictStrategy
import com.tencent.bkrepo.replication.pojo.RemoteProjectInfo
import com.tencent.bkrepo.replication.pojo.RemoteRepoInfo
import com.tencent.bkrepo.replication.pojo.ReplicationProjectDetail
import com.tencent.bkrepo.replication.pojo.ReplicationRepoDetail
import com.tencent.bkrepo.replication.pojo.ReplicationStatus
import com.tencent.bkrepo.replication.repository.TaskRepository
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.ProjectResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.apache.commons.fileupload.util.Streams
import org.quartz.DisallowConcurrentExecution
import org.quartz.JobExecutionContext
import org.quartz.PersistJobDataAfterExecution
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.scheduling.quartz.QuartzJobBean
import java.time.Duration
import java.time.LocalDateTime

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
class FullReplicaJob : QuartzJobBean() {

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var projectResource: ProjectResource

    @Autowired
    private lateinit var repositoryResource: RepositoryResource

    @Autowired
    private lateinit var nodeResource: NodeResource

    @Autowired
    private lateinit var permissionResource: ServicePermissionResource

    @Autowired
    private lateinit var roleResource: ServiceRoleResource

    @Autowired
    private lateinit var userResource: ServiceUserResource

    @Autowired
    private lateinit var storageService: StorageService

    @Value("\${spring.application.version}")
    private var version: String = ""

    override fun executeInternal(context: JobExecutionContext) {
        val taskId = context.jobDetail.jobDataMap.getString(TASK_ID_KEY)
        logger.info("Start to replication task[$taskId].")
        val task = taskRepository.findByIdOrNull(taskId) ?: run {
            logger.error("Task info[$taskId] does not exist.")
            return
        }

        try {
            with(task.setting) {
                val replicaResource = FeignClientFactory.create(ReplicaResource::class.java, remoteClusterInfo)
                val replicaContext = ReplicaJobContext(task, replicaResource)
                // 更新状态
                task.status = ReplicationStatus.REPLICATING
                task.startTime = LocalDateTime.now()
                // 检查版本
                checkVersion(replicaResource)
                // 查询同步详情信息
                queryReplicaDetail(replicaContext)
                taskRepository.save(task)
                // 开始同步
                replica(replicaContext)
                // 更新状态
                task.status = ReplicationStatus.SUCCESS
            }
        } catch (exception: Exception) {
            // 记录异常
            task.status = ReplicationStatus.ERROR
            task.errorReason = exception.message
        } finally {
            // 保存结果
            task.endTime = LocalDateTime.now()
            taskRepository.save(task)
            val consumeSeconds = Duration.between(task.startTime!!, task.endTime!!).seconds
            logger.info("Replica task[$taskId] is finished[${task.status}], reason[${task.errorReason}], consume [$consumeSeconds]s.")
        }
    }

    private fun replica(context: ReplicaJobContext) {
        context.detailList.forEach {
            replicaProject(it, context)
            context.task.replicaProgress.replicatedProject += 1
        }
    }

    private fun checkVersion(replicaResource: ReplicaResource) {
        val remoteVersion = replicaResource.version().data!!
        if (version != remoteVersion) {
            logger.warn("The local cluster's version[$version] is different from remote cluster's version[$remoteVersion]")
        }
    }

    private fun queryReplicaDetail(context: ReplicaJobContext) {
        with(context) {
            val detailList = mutableListOf<ReplicationProjectDetail>()
            when {
                // 同步所有
                task.setting.includeAllProject -> {
                    val projectList = replicaResource.listProject(authToken).data!!
                    val repoList = projectList.map {
                            info -> convertReplicationProject(info)
                    }
                    detailList.addAll(repoList)
                }
                // 同步指定项目
                task.setting.replicationProjectList != null -> {
                    task.setting.replicationProjectList!!.forEach {
                        val projectList = replicaResource.listProject(authToken, it.remoteProjectId).data!!
                        val repoList = projectList.map {
                                info -> convertReplicationProject(info, it.selfProjectId)
                        }
                        detailList.addAll(repoList)
                    }
                }
                // 同步指定仓库
                task.setting.replicationRepoList != null -> {
                    task.setting.replicationRepoList!!.forEach {
                        val projectList = replicaResource.listProject(authToken, it.remoteProjectId, it.remoteRepoName).data!!
                        val repoList = projectList.map {
                                info -> convertReplicationProject(info, it.selfProjectId, it.selfRepoName)
                        }
                        detailList.addAll(repoList)
                    }
                }
                else -> logger.warn("None repository need to be replicated.")
            }

            task.replicaProgress.totalProject = detailList.size
            detailList.forEach { project ->
                task.replicaProgress.totalRepo += project.repoList.size
                project.repoList.forEach { repo -> task.replicaProgress.totalNode += repo.count }
            }
            context.detailList = detailList
        }
    }

    private fun replicaProject(projectDetail: ReplicationProjectDetail, context: ReplicaJobContext) {
        with(projectDetail) {
            context.projectDetail = projectDetail
            context.remoteProject = remoteProject
            // 创建项目
            context.selfProject = createProject(context)
            // 同步仓库
            projectDetail.repoList.forEach {
                replicaRepo(it, context)
                context.task.replicaProgress.replicatedRepo += 1
            }
        }
    }

    private fun replicaRepo(repoDetail: ReplicationRepoDetail, context: ReplicaJobContext) {
        with(context) {
            // 创建仓库
            this.repoDetail = repoDetail
            this.remoteRepo = repoDetail.remoteRepo
            this.selfRepo = createRepo(context)
            // 同步节点
            var page = 0
            var fileNodeList = replicaResource.listFileNode(authToken, remoteRepo.projectId, remoteRepo.name, page, pageSize).data!!.records
            while (fileNodeList.isNotEmpty()) {
                fileNodeList.forEach { replicaNode(it, context) }
                page += 1
                fileNodeList = replicaResource.listFileNode(authToken, remoteRepo.projectId, remoteRepo.name, page, pageSize).data!!.records
            }
        }
    }

    private fun replicaNode(node: NodeInfo, context: ReplicaJobContext) {
        with(context) {
            // 节点冲突检查
            if (nodeResource.exist(selfRepo.projectId, selfRepo.name, node.fullPath).data == true) {
                when (task.setting.conflictStrategy) {
                    ConflictStrategy.SKIP -> {
                        logger.warn("Node[$node] conflict, skip it.")
                        task.replicaProgress.conflictedNode += 1
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
                    replicaResource.getMetadata(authToken, node.projectId, node.repoName, node.fullPath).data!!
                } else null
                // 下载数据
                val response = replicaResource.downloadFile(authToken, node.projectId, node.repoName, node.fullPath)
                if (response.status() != HttpStatus.OK.value()) {
                    throw RuntimeException("Download file[${node.projectId}/${node.repoName}/${node.fullPath}] error, reason: ${response.reason()}")
                }
                // 保存数据
                val file = ArtifactFileFactory.build()
                Streams.copy(response.body().asInputStream(), file.getOutputStream(), true)
                storageService.store(node.sha256!!, file, selfRepo.storageCredentials)
                // 创建节点
                val request = NodeCreateRequest(
                    projectId = selfRepo.projectId,
                    repoName = selfRepo.name,
                    fullPath = node.fullPath,
                    folder = false,
                    size = node.size,
                    overwrite = true,
                    sha256 = node.sha256,
                    md5 = node.md5,
                    metadata = metadata
                )
                nodeResource.create(request)
                task.replicaProgress.successNode += 1
            } catch (exception: Exception) {
                logger.error("Replica node[$node] error.", exception)
                task.replicaProgress.errorNode += 1
            } finally {
                if (task.replicaProgress.getReplicatedNode() % 10 == 0L) {
                    taskRepository.save(task)
                }
            }
        }
    }

    private fun createProject(context: ReplicaJobContext): ProjectInfo {
        with(context) {
            return projectResource.query(projectDetail.selfProjectId).data ?: run {
                val request = ProjectCreateRequest(
                    name = projectDetail.selfProjectId,
                    displayName = remoteProject.displayName,
                    description = remoteProject.description
                )
                val project = projectResource.create(request).data!!
                // 创建权限
                replicaUserAndPermission(context)
                project
            }
        }
    }

    private fun createRepo(context: ReplicaJobContext): RepositoryInfo {
        with(context) {
            val selfProjectId = projectDetail.selfProjectId
            val selfRepoName = repoDetail.selfRepoName
            return repositoryResource.detail(selfProjectId, selfRepoName, remoteRepo.type.name).data ?: run {
                val request = RepoCreateRequest(
                    projectId = selfProjectId,
                    name = selfRepoName,
                    type = remoteRepo.type,
                    category = remoteRepo.category,
                    public = remoteRepo.public,
                    description = remoteRepo.description,
                    configuration = remoteRepo.configuration
                )
                val repo = repositoryResource.create(request).data!!
                // 创建权限
                replicaUserAndPermission(context, true)
                repo
            }
        }
    }

    private fun replicaUserAndPermission(context: ReplicaJobContext, isRepo: Boolean = false) {
        with(context) {
            if (task.setting.includePermission) {
                return
            }
            val remoteProjectId = context.remoteProject.name
            val remoteRepoName = if (isRepo) context.remoteRepo.name else null
            val selfProjectId = context.selfProject.name
            val selfRepoName = if (isRepo) context.selfRepo.name else null
            // 1. 查询所有相关联的角色
            val roleList = replicaResource.listRole(authToken, remoteProjectId, remoteRepoName).data!!
            // 2. 查询所有角色所关联的用户
            val userList = replicaResource.listUser(authToken, roleList.map { it.id!! }).data!!
            val traversedUserList = mutableListOf<String>()
            val roleIdMap = mutableMapOf<String, String>()
            userList.forEach {
                createUser(it)
                traversedUserList.add(it.userId)
            }
            roleList.forEach { role ->
                // 创建角色
                val selfRoleId = createRole(role, selfProjectId, selfRepoName)
                roleIdMap[role.roleId] = selfRoleId
                // 包含该角色的用户列表
                val userIdList = userList.filter { user ->
                    user.roles.contains(role.id)
                }.map { user -> user.userId }
                userResource.addUserRoleBatch(selfRoleId, userIdList)
            }
            // 同步权限数据
            val permissionList = replicaResource.listPermission(authToken, remoteProjectId, remoteRepoName).data!!
            permissionList.forEach { permission ->
                // 创建用户
                permission.users.forEach {
                    if (!traversedUserList.contains(it.id)) {
                        createUser(it.id, context)
                    }
                }
                // 查询对应id
                val roles = permission.roles.map {
                    PermissionSet(roleIdMap[it.id]!!, it.action)
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
                permissionResource.createPermission(request)
            }
        }
    }

    private fun createRole(role: Role, projectId: String, repoName: String? = null): String {
        val existedRole = if (repoName == null) {
            roleResource.detailByRidAndProjectId(role.roleId, role.projectId).data
        } else {
            roleResource.detailByRidAndProjectIdAndRepoName(role.roleId, projectId, repoName).data
        }

        return existedRole?.id ?: run {
            val request = CreateRoleRequest(
                roleId = role.roleId,
                name = role.name,
                type = role.type,
                projectId = projectId,
                repoName = repoName,
                admin = role.admin
            )
            roleResource.createRole(request).data!!
        }
    }

    private fun createUser(uid: String, context: ReplicaJobContext) {
        with(context) {
            val remoteUser = replicaResource.getUserDetail(authToken, uid).data!!
            createUser(remoteUser)
        }
    }

    private fun createUser(user: User) {
        val selfUser = userResource.detail(user.userId).data ?: run {
            val request = CreateUserRequest(
                userId = user.userId,
                name = user.name,
                pwd = user.pwd,
                admin = user.admin
            )
            userResource.createUser(request)
            userResource.detail(user.userId).data!!
        }
        user.tokens.forEach {
            if (!selfUser.tokens.contains(it)) {
                userResource.addUserToken(user.userId, it.id)
            }
        }
    }

    private fun convertReplicationProject(
        remoteProjectInfo: RemoteProjectInfo,
        selfProjectId: String? = null,
        selfRepoName: String? = null
    ): ReplicationProjectDetail {
        return with(remoteProjectInfo) {
            ReplicationProjectDetail(
                remoteProject = this.project,
                repoList = this.repoList.map { convertReplicationRepo(it, selfRepoName) },
                selfProjectId = selfProjectId ?: this.project.name
            )
        }
    }

    private fun convertReplicationRepo(
        remoteRepoInfo: RemoteRepoInfo,
        selfRepoName: String? = null
    ): ReplicationRepoDetail {
        return with(remoteRepoInfo) {
            ReplicationRepoDetail(
                remoteRepo = this.repo,
                count = this.count,
                selfRepoName = selfRepoName ?: this.repo.name,
                includeAllNode = true
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FullReplicaJob::class.java)
        private const val pageSize = 500
    }
}
