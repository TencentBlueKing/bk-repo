package com.tencent.bkrepo.replication.job

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
import com.tencent.bkrepo.replication.pojo.ReplicationSetting
import com.tencent.bkrepo.replication.pojo.ReplicationStatus
import com.tencent.bkrepo.replication.repository.TaskRepository
import com.tencent.bkrepo.repository.api.MetadataResource
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.ProjectResource
import com.tencent.bkrepo.repository.api.RepositoryResource
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
import org.springframework.scheduling.quartz.QuartzJobBean
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
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var metadataResource: MetadataResource

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
                val replicaResource = FeignClientFactory.create(ReplicaResource::class.java, remoteClusterInfo.url)
                // 检查版本
                checkVersion(replicaResource)
                // 查询同步详情信息
                val detailList = queryReplicaDetail(this, replicaResource)
                task.replicaProgress.totalProject = detailList.size
                detailList.forEach { project ->
                    task.replicaProgress.totalRepo += project.repoList.size
                    project.repoList.forEach { repo -> task.replicaProgress.totalNode += repo.count }
                }
                // 更新状态
                task.status = ReplicationStatus.REPLICATING
                task.startTime = LocalDateTime.now()
                taskRepository.save(task)
                val replicaContext = ReplicaJobContext(task, replicaResource)
                // 开始同步
                detailList.forEach {
                    replicaProject(it, replicaContext)
                    task.replicaProgress.replicatedProject += 1
                }
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
        }
    }

    private fun checkVersion(replicaResource: ReplicaResource) {
        val remoteVersion = replicaResource.version().data!!
        if (version != remoteVersion) {
            logger.warn("The local cluster's version[$version] is different from remote cluster's version[$remoteVersion]")
        }
    }

    private fun queryReplicaDetail(setting: ReplicationSetting, replicaResource: ReplicaResource): List<ReplicationProjectDetail> {
        val replicaDetailList = mutableListOf<ReplicationProjectDetail>()
        when {
            // 同步所有
            setting.includeAllProject -> {
                replicaDetailList.addAll(replicaResource.listProject().data!!.map { convertReplicationProject(it) })
            }
            // 同步指定项目
            setting.replicationProjectReplicaList != null -> {
                setting.replicationProjectReplicaList!!.forEach {
                    replicaDetailList.addAll(replicaResource.listProject(it.remoteProjectId).data!!.map {
                        info -> convertReplicationProject(info, it.selfProjectId)
                    })
                }
            }
            // 同步指定仓库
            setting.replicationList != null -> {
                setting.replicationList!!.forEach {
                    replicaDetailList.addAll(replicaResource.listProject(it.remoteProjectId, it.remoteRepoName).data!!.map {
                        info -> convertReplicationProject(info, it.selfProjectId, it.selfRepoName)
                    })
                }
            }
            else -> logger.warn("None repository need to be replicated.")
        }
        return replicaDetailList
    }

    private fun replicaProject(projectDetail: ReplicationProjectDetail, context: ReplicaJobContext) {
        with(projectDetail) {
            // 创建项目
            createProject(remoteProject, selfProjectId)
            // 同步仓库
            projectDetail.repoList.forEach {
                replicaRepo(it, selfProjectId, context)
                context.task.replicaProgress.replicatedRepo += 1
            }
        }
    }

    private fun replicaRepo(repoDetail: ReplicationRepoDetail, selfProjectId: String, context: ReplicaJobContext) {
        with(repoDetail) {
            // 创建仓库
            val selfRepo = queryOrCreateRepo(remoteRepo, selfProjectId, selfRepoName)
            // 分页查询节点
            var page = 0
            var fileNodeList = context.replicaResource.listFileNode(remoteRepo.projectId, remoteRepo.name, page, pageSize).data!!
            while (fileNodeList.isNotEmpty()) {
                fileNodeList.forEach { replicaNode(it, selfRepo, context) }
                page += 1
                fileNodeList = context.replicaResource.listFileNode(remoteRepo.projectId, remoteRepo.name, page, pageSize).data!!
            }
        }
    }

    private fun replicaNode(node: NodeInfo, repo: RepositoryInfo, context: ReplicaJobContext) {
        // 节点冲突检查
        if (nodeResource.exist(repo.projectId, repo.name, node.fullPath).data == true) {
            when (context.task.setting.conflictStrategy) {
                ConflictStrategy.SKIP -> {
                    logger.warn("Node[$node] conflict, skip it.")
                    context.task.replicaProgress.conflictedNode += 1
                    return
                }
                ConflictStrategy.OVERWRITE -> {
                    logger.warn("Node[$node] conflict, overwrite it.")
                }
                ConflictStrategy.FAST_FAIL -> throw RuntimeException("Node[$node] conflict.")
            }
        }
        try {
            // 查询元数据
            val metadata = if (context.task.setting.includeMetadata) {
                context.replicaResource.getMetadata(node.projectId, node.repoName, node.fullPath).data!!
            } else null
            // 下载数据
            val response = context.replicaResource.downloadFile(node.projectId, node.repoName, node.fullPath)
            // 保存数据
            val file = ArtifactFileFactory.build()
            Streams.copy(response.body().asInputStream(), file.getOutputStream(), true)
            storageService.store(node.sha256!!, file, repo.storageCredentials)
            // 创建节点
            val request = NodeCreateRequest(
                projectId = repo.projectId,
                repoName = repo.name,
                fullPath = node.fullPath,
                folder = false,
                size = node.size,
                overwrite = true,
                sha256 = node.sha256,
                md5 = node.md5,
                metadata = metadata
            )
            nodeResource.create(request)
            context.task.replicaProgress.successNode += 1
        } catch (exception: Exception) {
            logger.error("Replica node[$node] error.", exception)
            context.task.replicaProgress.errorNode += 1
        } finally {
            if (context.task.replicaProgress.getReplicatedNode() % 10 == 0L) {
                taskRepository.save(context.task)
            }
        }
    }

    private fun createProject(remoteProject: ProjectInfo, selfProjectId: String) {
        with(remoteProject) {
            if (projectResource.query(selfProjectId).data == null) {
                val request = ProjectCreateRequest(
                    name = selfProjectId,
                    displayName = this.displayName,
                    description = this.description
                )
                projectResource.create(request)
            }
        }
    }

    private fun queryOrCreateRepo(remoteRepo: RepositoryInfo, selfProjectId: String, selfRepoName: String): RepositoryInfo {
        with(remoteRepo) {
            return repositoryResource.detail(selfProjectId, selfRepoName, type.name).data ?: run {
                val request = RepoCreateRequest(
                    projectId = selfProjectId,
                    name = selfRepoName,
                    type = this.type,
                    category = this.category,
                    public = this.public,
                    description = this.description,
                    configuration = this.configuration
                )
                repositoryResource.create(request)
                repositoryResource.detail(selfProjectId, selfRepoName).data!!
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
