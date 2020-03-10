package com.tencent.bkrepo.replication.resource

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.replication.api.ReplicaResource
import com.tencent.bkrepo.replication.pojo.RemoteProjectInfo
import com.tencent.bkrepo.replication.pojo.RemoteRepoInfo
import com.tencent.bkrepo.repository.api.MetadataResource
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.ProjectResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.RestController

@RestController
class ReplicaResourceImpl : ReplicaResource {

    @Value("\${spring.application.version}")
    private var version: String = ""

    @Autowired
    private lateinit var projectResource: ProjectResource

    @Autowired
    private lateinit var repositoryResource: RepositoryResource

    @Autowired
    private lateinit var nodeResource: NodeResource

    @Autowired
    private lateinit var metadataResource: MetadataResource

    @Autowired
    private lateinit var storageService: StorageService

    override fun ping(): Response<Void> {
        return ResponseBuilder.success()
    }

    override fun version(): Response<String> {
        return ResponseBuilder.success(version)
    }

    override fun listProject(projectId: String?, repoName: String?): Response<List<RemoteProjectInfo>> {
        val remoteProjectList = if (projectId == null && repoName == null) {
            projectResource.list().data!!.map { project ->
                val repoList = repositoryResource.list(project.name).data!!.map { repo ->
                    val count = nodeResource.countFileNode(repo.projectId, repo.name, "/").data!!
                    RemoteRepoInfo(repo, count)
                }
                RemoteProjectInfo(project, repoList)
            }
        } else if (projectId != null && repoName == null) {
            projectResource.query(projectId).data?.let { project ->
                val repoList = repositoryResource.list(project.name).data!!.map { repo ->
                    val count = nodeResource.countFileNode(repo.projectId, repo.name, "/").data!!
                    RemoteRepoInfo(repo, count)
                }
                listOf(RemoteProjectInfo(project, repoList))
            } ?: emptyList()
        } else if (projectId != null && repoName != null) {
            projectResource.query(projectId).data?.let { project ->
                val repoList = repositoryResource.detail(projectId, repoName).data?.let { repo ->
                    val count = nodeResource.countFileNode(repo.projectId, repo.name, "/").data!!
                    RemoteRepoInfo(repo, count)
                }
                repoList?.let { listOf(RemoteProjectInfo(project, listOf(it))) }
            } ?: emptyList()
        } else emptyList()

        return ResponseBuilder.success(remoteProjectList)
    }

    override fun listFileNode(projectId: String, repoName: String, page: Int, size: Int, path: String): Response<Page<NodeInfo>> {
        return nodeResource.page(projectId, repoName, page, size, path, includeFolder = false, deep = true)
    }

    override fun getMetadata(projectId: String, repoName: String, fullPath: String): Response<Map<String, String>> {
        return metadataResource.query(projectId, repoName, fullPath)
    }

    override fun downloadFile(projectId: String, repoName: String, fullPath: String): feign.Response {
        val repoInfo = repositoryResource.detail(projectId, repoName).data!!
        val nodeInfo = nodeResource.detail(projectId, repoName, fullPath).data!!
        val file = storageService.load(nodeInfo.nodeInfo.sha256!!, repoInfo.storageCredentials)!!
        return feign.Response.builder().body(file.inputStream(), file.length().toInt()).build()
    }
}
