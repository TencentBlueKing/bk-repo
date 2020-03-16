package com.tencent.bkrepo.replication.resource

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.api.ServiceRoleResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.enums.RoleType
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
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PathVariable
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
    private lateinit var permissionResource: ServicePermissionResource

    @Autowired
    private lateinit var userResource: ServiceUserResource

    @Autowired
    private lateinit var roleResource: ServiceRoleResource

    @Autowired
    private lateinit var storageService: StorageService

    override fun ping(): Response<Void> {
        return ResponseBuilder.success()
    }

    override fun version(): Response<String> {
        return ResponseBuilder.success(version)
    }

    override fun listProject(token: String, projectId: String?, repoName: String?): Response<List<RemoteProjectInfo>> {
        val remoteProjectList = if (projectId == null && repoName == null) {
            projectResource.list().data!!.map { project ->
                val repoList = repositoryResource.list(project.name).data!!.map {
                    repo -> convertRemoteRepoInfo(repo)
                }
                RemoteProjectInfo(project, repoList)
            }
        } else if (projectId != null && repoName == null) {
            projectResource.query(projectId).data?.let { project ->
                val repoList = repositoryResource.list(project.name).data!!.map {
                    repo -> convertRemoteRepoInfo(repo)
                }
                listOf(RemoteProjectInfo(project, repoList))
            } ?: emptyList()
        } else if (projectId != null && repoName != null) {
            projectResource.query(projectId).data?.let { project ->
                val repoList = repositoryResource.detail(projectId, repoName).data?.let {
                    repo -> convertRemoteRepoInfo(repo)
                }
                repoList?.let { listOf(RemoteProjectInfo(project, listOf(it))) }
            } ?: emptyList()
        } else {
            emptyList()
        }

        return ResponseBuilder.success(remoteProjectList)
    }

    override fun listFileNode(token: String, projectId: String, repoName: String, page: Int, size: Int, path: String): Response<Page<NodeInfo>> {
        return nodeResource.page(projectId, repoName, page, size, path, includeFolder = false, deep = true)
    }

    override fun listPermission(token: String, projectId: String, repoName: String?): Response<List<Permission>> {
        val resourceType = if (repoName == null) ResourceType.PROJECT else ResourceType.REPO
        return permissionResource.listPermission(resourceType, projectId, repoName)
    }

    override fun listUser(token: String, @PathVariable roleIdList: List<String>): Response<List<User>> {
        return userResource.listUser(roleIdList)
    }

    override fun getUserDetail(token: String, uid: String): Response<User?> {
        return userResource.detail(uid)
    }

    override fun listRole(token: String, projectId: String, repoName: String?): Response<List<Role>> {
        val roleType = if (repoName == null) RoleType.PROJECT else RoleType.REPO
        return roleResource.listRole(roleType, projectId, repoName)
    }

    override fun getMetadata(token: String, projectId: String, repoName: String, fullPath: String): Response<Map<String, String>> {
        return metadataResource.query(projectId, repoName, fullPath)
    }

    override fun downloadFile(token: String, projectId: String, repoName: String, fullPath: String): feign.Response {
        val repoInfo = repositoryResource.detail(projectId, repoName).data!!
        val nodeInfo = nodeResource.detail(projectId, repoName, fullPath).data!!
        val file = storageService.load(nodeInfo.nodeInfo.sha256!!, repoInfo.storageCredentials)!!
        return feign.Response.builder().body(file.inputStream(), file.length().toInt()).build()
    }

    private fun convertRemoteRepoInfo(repo: RepositoryInfo): RemoteRepoInfo {
        val count = nodeResource.countFileNode(repo.projectId, repo.name, "/").data!!
        return RemoteRepoInfo(repo, count)
    }
}
