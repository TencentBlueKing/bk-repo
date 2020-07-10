package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.api.ServiceRoleResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.common.api.constant.StringPool.ROOT
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.MetadataResource
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.ProjectResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class RepoDataService @Autowired constructor(
    private val projectResource: ProjectResource,
    private val repositoryResource: RepositoryResource,
    private val nodeResource: NodeResource,
    private val metadataResource: MetadataResource,
    private val permissionResource: ServicePermissionResource,
    private val roleResource: ServiceRoleResource,
    private val userResource: ServiceUserResource,
    private val storageService: StorageService
) {

    fun listProject(projectId: String? = null): List<ProjectInfo> {
        return if (projectId == null) {
            projectResource.list().data!!
        } else {
            listOf(projectResource.query(projectId).data!!)
        }
    }

    fun listRepository(projectId: String, repoName: String? = null): List<RepositoryInfo> {
        return if (repoName == null) {
            repositoryResource.list(projectId).data!!
        } else {
            listOf(repositoryResource.detail(projectId, repoName).data!!)
        }
    }

    fun getRepositoryDetail(projectId: String, repoName: String): RepositoryInfo? {
        return repositoryResource.detail(projectId, repoName).data
    }

    fun countFileNode(repositoryInfo: RepositoryInfo): Long {
        return nodeResource.countFileNode(repositoryInfo.projectId, repositoryInfo.name, ROOT).data!!
    }

    fun listFileNode(projectId: String, repoName: String, path: String = ROOT, page: Int = 0, size: Int = 100): List<NodeInfo> {
        return nodeResource.page(projectId, repoName, page, size, path, includeFolder = false, deep = true).data!!.records
    }

    fun getMetadata(nodeInfo: NodeInfo): Map<String, String> {
        return metadataResource.query(nodeInfo.projectId, nodeInfo.repoName, nodeInfo.fullPath).data!!
    }

    fun getFile(sha256: String, length: Long, repoInfo: RepositoryInfo): InputStream {
        return storageService.load(sha256, Range.ofFull(length), repoInfo.storageCredentials) ?:
            throw RuntimeException("File data does not exist")
    }

    fun listRole(projectId: String, repoName: String?): List<Role> {
        val roleType = if (repoName == null) RoleType.PROJECT else RoleType.REPO
        return roleResource.listRole(roleType, projectId, repoName).data!!
    }

    fun listUser(roleIdList: List<String>): List<User> {
        return userResource.listUser(roleIdList).data!!
    }

    fun listPermission(resourceType: ResourceType, projectId: String, repoName: String?): List<Permission> {
        return permissionResource.listPermission(resourceType, projectId, repoName).data!!
    }

    fun getUserDetail(uid: String): User? {
        return userResource.detail(uid).data
    }
}
