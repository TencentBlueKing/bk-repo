package com.tencent.bkrepo.maven.artifact.repository

import com.tencent.bkrepo.common.api.util.readXmlString
import com.tencent.bkrepo.common.api.util.toXmlString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.maven.pojo.MavenArtifact
import com.tencent.bkrepo.maven.pojo.Basic
import com.tencent.bkrepo.maven.pojo.MavenArtifactVersionData
import com.tencent.bkrepo.maven.pojo.MavenMetadata
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream

@Component
class MavenLocalRepository : LocalRepository() {

    /**
     * 获取MAVEN节点创建请求
     */
    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val request = super.buildNodeCreateRequest(context)
        return request.copy(overwrite = true)
    }

    fun metadataNodeCreateRequest(
        context: ArtifactUploadContext,
        fullPath: String,
        metadataArtifact: ArtifactFile
    ): NodeCreateRequest {
        val request = super.buildNodeCreateRequest(context)
        return request.copy(
            overwrite = true,
            fullPath = fullPath
        )
    }

    fun updateMetadata(fullPath: String, metadataArtifact: ArtifactFile) {
        val uploadContext = ArtifactUploadContext(metadataArtifact)
        val metadataNode = metadataNodeCreateRequest(uploadContext, fullPath, metadataArtifact)
        storageManager.storeArtifactFile(metadataNode, metadataArtifact, uploadContext.storageCredentials)
        logger.info("Success to save $fullPath, size: ${metadataArtifact.getSize()}")
    }

    override fun remove(context: ArtifactRemoveContext) {
        updateMetadataXml(context)
        with(context.artifactInfo) {
            nodeClient.delete(
                NodeDeleteRequest(
                    projectId,
                    repoName,
                    getArtifactFullPath(),
                    ArtifactRemoveContext().userId
                )
            )
            logger.info("Success to delete ${context.artifactInfo.getArtifactFullPath()}")
        }
    }

    /**
     * 删除jar包时 对包一级目录下maven-metadata.xml 更新
     */
    fun updateMetadataXml(context: ArtifactRemoveContext) {
        val mavenArtifactInfo = context.artifactInfo
        val fullPath = mavenArtifactInfo.getArtifactFullPath()
        val pathList = fullPath.split("/")
        val version = pathList.last()
        val packagePath = fullPath.removeSuffix(version)
        // 加载xml
        with(mavenArtifactInfo) {
            val nodeList = nodeClient.list(projectId, repoName, packagePath).data ?: return
            val mavenMetadataNode = nodeList.filter { it.name == "maven-metadata.xml" }[0]
            val artifactInputStream = storageService.load(
                mavenMetadataNode.sha256!!,
                Range.full(mavenMetadataNode.size),
                ArtifactRemoveContext().storageCredentials
            ) ?: return
            val xmlStr = String(artifactInputStream.readBytes()).removePrefix("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            val mavenMetadata = xmlStr.readXmlString<MavenMetadata>()
            mavenMetadata.versioning.versions.version.removeIf { it == version }

            if (mavenMetadata.versioning.versions.version.size == 0) {
                nodeClient.delete(
                        NodeDeleteRequest(
                                projectId,
                                repoName,
                                packagePath,
                                ArtifactRemoveContext().userId
                        )
                )
            } else {
                mavenMetadata.versioning.release = mavenMetadata.versioning.versions.version.last()
                val resultXml = mavenMetadata.toXmlString()
                val resultXmlMd5 = resultXml.md5()
                val resultXmlSha1 = resultXml.sha1()

                val metadataArtifact = ByteArrayInputStream(resultXml.toByteArray()).use {
                    ArtifactFileFactory.build(it) }
                val metadataArtifactMd5 = ByteArrayInputStream(resultXmlMd5.toByteArray()).use {
                    ArtifactFileFactory.build(it) }
                val metadataArtifactSha1 = ByteArrayInputStream(resultXmlSha1.toByteArray()).use {
                    ArtifactFileFactory.build(it) }

                logger.warn("${metadataArtifact.getSize()}")
                updateMetadata("${packagePath}maven-metadata.xml", metadataArtifact)
                metadataArtifact.delete()
                updateMetadata("${packagePath}maven-metadata.xml.md5", metadataArtifactMd5)
                metadataArtifactMd5.delete()
                updateMetadata("${packagePath}maven-metadata.xml.sha1", metadataArtifactSha1)
                metadataArtifactSha1.delete()
            }
        }
    }

    override fun query(context: ArtifactQueryContext): MavenArtifactVersionData? {
        val artifactInfo = context.artifactInfo
        val pathList = artifactInfo.getArtifactFullPath()
                .removePrefix("/")
                .removeSuffix("/")
                .split("/")
        val version = pathList.last()
        val artifactId = pathList[pathList.size - 2]
        val groupId = StringUtils.join(pathList.subList(0, pathList.size-1), ".")
        with(artifactInfo) {
            val nodeDetail = nodeClient.list(projectId, repoName, getArtifactFullPath()).data ?: return null
            val jarNode = nodeDetail.filter { it.name.matches(Regex("(.)+-(.)+.jar")) }[0]
            //TODO
//            val countData = downloadStatisticsClient.query(projectId, repoName, jarNode.fullPath.removePrefix("/"),
//                    null, null, null).data
//            val count = countData?.count ?: 0
            val mavenArtifactBasic = Basic(
                    groupId,
                    artifactId,
                    version,
                    jarNode.size, jarNode.fullPath, jarNode.lastModifiedBy, jarNode.lastModifiedDate,
                    0L,
                    jarNode.sha256,
                    jarNode.md5,
                    null
            )
            val mavenArtifactMetadata = jarNode.metadata?: mutableMapOf()
            return MavenArtifactVersionData(mavenArtifactBasic, mavenArtifactMetadata)
        }
    }

    override fun search(context: ArtifactSearchContext): List<MavenArtifact> {
        val artifactInfo = context.artifactInfo
        val pathList = artifactInfo.getArtifactFullPath()
                .removePrefix("/")
                .removeSuffix("/")
                .split("/")
        val artifactId = pathList.last()
        val list = mutableListOf<MavenArtifact>()
        with(artifactInfo) {
            val versionList = nodeClient.list(projectId, repoName, getArtifactFullPath()).data ?: return mutableListOf()
            //验证版本目录下是否有jar包
            for (versionNode in versionList) {
                val version = versionNode.name
                val fullPath = "${getArtifactFullPath()}/$version/$artifactId-$version.jar"
                val nodeDetail = nodeClient.detail(projectId, repoName, fullPath).data ?: continue
                val groupId = StringUtils.join(pathList.subList(0, pathList.size-1), ".")
                list.add(MavenArtifact(groupId, artifactId, version, nodeDetail))
            }
        }
        return list
    }

    companion object{
        private val logger: Logger = LoggerFactory.getLogger(MavenLocalRepository::class.java)
    }
}
