package com.tencent.bkrepo.rpm.servcie

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.rpm.FILELISTS_XML
import com.tencent.bkrepo.rpm.OTHERS_XML
import com.tencent.bkrepo.rpm.PRIMARY_XML
import com.tencent.bkrepo.rpm.REPOMD_XML
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.artifact.repository.RpmLocalRepository
import com.tencent.bkrepo.rpm.util.RpmCollectionUtils.updateList
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RpmService {

    @Autowired
    lateinit var repositoryClient: RepositoryClient

    // groups 中不允许的元素
    private val rpmIndexSet = mutableSetOf(REPOMD_XML, FILELISTS_XML, OTHERS_XML, PRIMARY_XML)

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun install(rpmArtifactInfo: RpmArtifactInfo) {
        val context = ArtifactDownloadContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.download(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun deploy(rpmArtifactInfo: RpmArtifactInfo, file: ArtifactFile) {
        val context = ArtifactUploadContext(file)
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        repository.upload(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun addGroups(rpmArtifactInfo: RpmArtifactInfo, groups: MutableSet<String>) {
        val context = ArtifactSearchContext()
        groups.removeAll(rpmIndexSet)
        val rpmLocalConfiguration = context.getLocalConfiguration()
        (rpmLocalConfiguration.getSetting<MutableList<String>>("groupXmlSet") ?: mutableListOf())
            .updateList(groups, true)
        val repoUpdateRequest = createRepoUpdateRequest(context, rpmLocalConfiguration)
        repositoryClient.update(repoUpdateRequest)
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        (repository as RpmLocalRepository).flushAllRepoData(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun deleteGroups(rpmArtifactInfo: RpmArtifactInfo, groups: MutableSet<String>) {
        val context = ArtifactSearchContext()
        val rpmLocalConfiguration = context.getLocalConfiguration()
        (rpmLocalConfiguration.getSetting<MutableList<String>>("groupXmlSet") ?: mutableListOf())
            .updateList(groups, false)
        val repoUpdateRequest = createRepoUpdateRequest(context, rpmLocalConfiguration)
        repositoryClient.update(repoUpdateRequest)
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        (repository as RpmLocalRepository).flushAllRepoData(context)
    }

    private fun createRepoUpdateRequest(
        context: ArtifactSearchContext,
        rpmLocalConfiguration: LocalConfiguration
    ): RepoUpdateRequest {
        return RepoUpdateRequest(
            context.artifactInfo.projectId,
            context.artifactInfo.repoName,
            context.repositoryDetail.public,
            context.repositoryDetail.description,
            rpmLocalConfiguration,
            context.userId
        )
    }
}
