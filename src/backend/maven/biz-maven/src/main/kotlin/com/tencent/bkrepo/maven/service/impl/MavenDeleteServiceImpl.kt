package com.tencent.bkrepo.maven.service.impl

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.maven.constants.MAVEN_METADATA_FILE_NAME
import com.tencent.bkrepo.maven.enum.HashType
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.service.MavenDeleteService
import com.tencent.bkrepo.maven.service.MavenMetadataService
import com.tencent.bkrepo.maven.util.MavenMetadataUtils.reRender
import com.tencent.bkrepo.maven.util.MavenUtil
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@Service
class MavenDeleteServiceImpl(
    private val packageClient: PackageClient,
    private val mavenMetadataService: MavenMetadataService,
    private val nodeClient: NodeClient,
    private val storageManager: StorageManager,
    private val repositoryClient: RepositoryClient
) : MavenDeleteService {
    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String,
        operator: String
    ): Boolean {
        packageClient.deleteVersion(projectId, repoName, packageKey, version)
        val artifactPath = MavenUtil.extractPath(packageKey) + "/$version"
        // 需要删除对应的metadata表记录
        val (artifactId, groupId) = MavenUtil.extractGroupIdAndArtifactId(packageKey)
        val mavenGAVC = MavenGAVC(groupId, artifactId, version, null)
        mavenMetadataService.delete(projectId, repoName, mavenGAVC)
        val request = NodeDeleteRequest(
            projectId = projectId,
            repoName = repoName,
            fullPath = artifactPath,
            operator = operator
        )
        nodeClient.deleteNode(request)
        // 更新包索引
        updatePackageMetadata(
            projectId,
            repoName,
            "${MavenUtil.extractPath(packageKey)}/$MAVEN_METADATA_FILE_NAME",
            mavenGAVC
        )
        return true
    }

    private fun updatePackageMetadata(
        projectId: String,
        repoName: String,
        artifactPath: String,
        mavenGavc: MavenGAVC
    ) {
        val repositoryDetail = repositoryClient.getRepoDetail(projectId, repoName, RepositoryType.MAVEN.name).data
            ?: throw RepoNotFoundException("repo not found: { projectId=$projectId, repoName=$repoName }")

        val node = nodeClient.getNodeDetail(projectId, repoName, artifactPath).data ?: return
        val operator = node.lastModifiedBy
        storageManager.loadArtifactInputStream(
            node,
            repositoryDetail.storageCredentials
        ).use { artifactInputStream ->
            // 更新 `/groupId/artifactId/maven-metadata.xml`
            val mavenMetadata = MetadataXpp3Reader().read(artifactInputStream)
            mavenMetadata.versioning.versions.remove(mavenGavc.version)
            if (mavenMetadata.versioning.versions.size == 0) {
                nodeClient.deleteNode(NodeDeleteRequest(projectId, repoName, node.fullPath, operator))
                return
            }
            mavenMetadata.reRender()
            storeMetadataXml(repositoryDetail, mavenMetadata, node, operator)
        }
    }

    private fun storeMetadataXml(
        repositoryDetail: RepositoryDetail,
        mavenMetadata: org.apache.maven.artifact.repository.metadata.Metadata,
        node: NodeDetail,
        operator: String
    ) {
        ByteArrayOutputStream().use { metadata ->
            MetadataXpp3Writer().write(metadata, mavenMetadata)
            val artifactFile = ArtifactFileFactory.build(metadata.toByteArray().inputStream())
            val resultXmlMd5 = artifactFile.getFileMd5()
            val resultXmlSha1 = artifactFile.getFileSha1()
            val metadataArtifactMd5 = ByteArrayInputStream(resultXmlMd5.toByteArray()).use {
                ArtifactFileFactory.build(it)
            }
            val metadataArtifactSha1 = ByteArrayInputStream(resultXmlSha1.toByteArray()).use {
                ArtifactFileFactory.build(it)
            }
            updateMetadata(repositoryDetail, "${node.path}/$MAVEN_METADATA_FILE_NAME", artifactFile, operator)
            artifactFile.delete()
            updateMetadata(
                repositoryDetail,
                "${node.path}/$MAVEN_METADATA_FILE_NAME.${HashType.MD5}",
                metadataArtifactMd5,
                operator
            )
            metadataArtifactMd5.delete()
            updateMetadata(
                repositoryDetail,
                "${node.path}/$MAVEN_METADATA_FILE_NAME.${HashType.SHA1}",
                metadataArtifactSha1,
                operator
            )
            metadataArtifactSha1.delete()
        }
    }

    private fun updateMetadata(
        repositoryDetail: RepositoryDetail,
        fullPath: String,
        metadataArtifact: ArtifactFile,
        operator: String
    ) {
        val metadataNode = NodeCreateRequest(
            projectId = repositoryDetail.projectId,
            repoName = repositoryDetail.name,
            fullPath = fullPath,
            folder = false,
            overwrite = true,
            size = metadataArtifact.getSize(),
            sha256 = metadataArtifact.getFileSha256(),
            md5 = metadataArtifact.getFileMd5(),
            operator = operator
        )
        storageManager.storeArtifactFile(metadataNode, metadataArtifact, repositoryDetail.storageCredentials)
        metadataArtifact.delete()
        logger.info("Success to save $fullPath, size: ${metadataArtifact.getSize()}")
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(MavenDeleteServiceImpl::class.java)
    }
}
