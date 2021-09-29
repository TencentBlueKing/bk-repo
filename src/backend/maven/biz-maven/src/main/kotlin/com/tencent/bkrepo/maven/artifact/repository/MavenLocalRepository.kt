/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.maven.artifact.repository

import com.tencent.bkrepo.common.api.util.toXmlString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.maven.FILENAME_REGEX
import com.tencent.bkrepo.maven.PACKAGE_SUFFIX_REGEX
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.pojo.Basic
import com.tencent.bkrepo.maven.pojo.MavenArtifactVersionData
import com.tencent.bkrepo.maven.pojo.MavenGAVC
import com.tencent.bkrepo.maven.util.MavenGAVCUtils.mavenGAVC
import com.tencent.bkrepo.maven.util.MavenRegexUtils
import com.tencent.bkrepo.maven.util.StringUtils.formatSeparator
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.StageClient
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.apache.commons.lang.StringUtils
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.util.regex.Pattern

@Component
class MavenLocalRepository(private val stageClient: StageClient) : LocalRepository() {

    /**
     * 获取MAVEN节点创建请求
     */
    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val request = super.buildNodeCreateRequest(context)
        return request.copy(overwrite = true)
    }

    private fun buildMavenArtifactNode(context: ArtifactUploadContext, packaging: String): NodeCreateRequest {
        val request = super.buildNodeCreateRequest(context)
        return request.copy(
            overwrite = true,
            metadata = mapOf("packaging" to packaging)
        )
    }

    override fun onUpload(context: ArtifactUploadContext) {
        val matcher = Pattern.compile(PACKAGE_SUFFIX_REGEX).matcher(context.artifactInfo.getArtifactFullPath())
        if (matcher.find()) {
            val packaging = matcher.group(3)
            if (packaging == "pom") {
                val mavenPomModel = context.getArtifactFile().getInputStream().use { MavenXpp3Reader().read(it) }
                if (StringUtils.isNotBlank(mavenPomModel.version)
                    && mavenPomModel.packaging.equals("pom", ignoreCase = true)
                ) {
                    val mavenPom = MavenGAVC(
                        groupId = mavenPomModel.groupId,
                        artifactId = mavenPomModel.artifactId,
                        version = mavenPomModel.version
                    )
                    val node = buildMavenArtifactNode(context, packaging)
                    storageManager.storeArtifactFile(node, context.getArtifactFile(), context.storageCredentials)
                    createMavenVersion(context, mavenPom)
                } else {
                    super.onUpload(context)
                }
            } else {
                val node = buildMavenArtifactNode(context, packaging)
                storageManager.storeArtifactFile(node, context.getArtifactFile(), context.storageCredentials)
                val mavenJar = (context.artifactInfo as MavenArtifactInfo).toMavenJar()
                createMavenVersion(context, mavenJar)
            }
        } else {
            super.onUpload(context)
        }
    }

    private fun createMavenVersion(context: ArtifactUploadContext, mavenGAVC: MavenGAVC) {
        packageClient.createVersion(
            PackageVersionCreateRequest(
                context.projectId,
                context.repoName,
                packageName = mavenGAVC.artifactId,
                packageKey = PackageKeys.ofGav(mavenGAVC.groupId, mavenGAVC.artifactId),
                packageType = PackageType.MAVEN,
                versionName = mavenGAVC.version,
                size = context.getArtifactFile().getSize(),
                artifactPath = context.artifactInfo.getArtifactFullPath(),
                overwrite = true,
                createdBy = context.userId
            )
        )
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
        val packageKey = context.request.getParameter("packageKey")
        val version = context.request.getParameter("version")
        val artifactId = packageKey.split(":").last()
        val groupId = packageKey.removePrefix("gav://").split(":")[0]
        with(context.artifactInfo) {
            if (version.isNullOrBlank()) {
                packageClient.deletePackage(
                    projectId,
                    repoName,
                    packageKey
                )
            } else {
                packageClient.deleteVersion(
                    projectId,
                    repoName,
                    packageKey,
                    version
                )
            }
            logger.info("Success to delete $packageKey:$version")
        }
        executeDelete(context, groupId, artifactId, version)
    }

    /**
     * 查找需要删除的文件列表
     */
    private fun getArtifactList(
        projectId: String,
        repoName: String,
        path: String,
        filename: String
    ): List<Map<String, Any?>>? {
        var i = 1
        while (true) {
            val nodeList = nodeClient.listNodePage(
                projectId, repoName, path, NodeListOption(
                    pageNumber = i++,
                    includeFolder = true
                )
            ).data ?: break
            if (nodeList.records.isEmpty()) break
            nodeList.records.filter { it.folder }.apply {
                for (node in this) {
                    val query = NodeQueryBuilder()
                        .select("fullPath")
                        .projectId(node.projectId)
                        .repoName(node.repoName)
                        .and()
                        .path("${node.fullPath}/")
                        .name("*$filename*", OperationType.MATCH)
                        .build()
                    val page = nodeClient.search(query).data
                    page?.let { page ->
                        if (page.records.isNotEmpty()) return page.records
                    }
                }
            }
        }
        return null
    }

    private fun findMetadata(projectId: String, repoName: String, path: String): NodeDetail? {
        return nodeClient.getNodeDetail(projectId, repoName, "${path}/maven-metadata.xml").data
    }


    /**
     * 删除jar包 关联文件 并修改该包的版本管理文件
     */
    fun executeDelete(context: ArtifactRemoveContext, groupId: String, artifactId: String, version: String?) {
//        val packageKey = context.request.getParameter("packageKey")
        val artifactPath = StringUtils.join(groupId.split("."), "/") + "/$artifactId"
        val filename = "$artifactId-$version"
        //删除 `/{groupId}/{artifact}/` 目录
        if (version.isNullOrBlank()) {
            nodeClient.deleteNode(
                NodeDeleteRequest(
                    context.projectId,
                    context.repoName,
                    artifactPath,
                    ArtifactRemoveContext().userId
                )
            )
            return
        }
        // 删除单个版本, 同时更新maven-metadata.xml
        with(context.artifactInfo) {
            getArtifactList(projectId, repoName, "$artifactPath/", filename)?.let {
                for (node in it) {
                    nodeClient.deleteNode(
                        NodeDeleteRequest(
                            projectId,
                            repoName,
                            node["fullPath"] as String,
                            ArtifactRemoveContext().userId
                        )
                    )
                }
                //检查 `/groupId/artifactId/versionId` 文件夹是否为空，为空删除该文件夹
                val fullPath = (it.first()["fullPath"] as String)
                val targetFolder = fullPath.substringBeforeLast("/")
                val deleteVersion = fullPath.substringAfterLast("/")
                if (checkFolder(projectId, repoName, targetFolder)) {
                    updateMetadata(projectId, repoName, targetFolder, null)
                } else {
                    val deleteVersion = fullPath.substringAfterLast("/")
                    updateMetadata(projectId, repoName, targetFolder, deleteVersion)
                }
//                val mavenMetadataNode = findMetadata(projectId, repoName, "$targetFolder/")
//                mavenMetadataNode?.let { node -> updateMetadata(node) }
            }
        }
    }

    /**
     */
    private fun checkFolder(projectId: String, repoName: String, path: String): Boolean {
        nodeClient.listNodePage(
            projectId, repoName, "$path/",
            NodeListOption(includeFolder = true)
        ).data.apply {
            if (this == null) {
                nodeClient.deleteNode(
                    NodeDeleteRequest(
                        projectId, repoName, "$path/", ArtifactRemoveContext().userId
                    )
                )
                return true
            }
        }
        return false
    }

    /**
     * [path] /groupId/artifactId/versionId
     */
    private fun updateMetadata(projectId: String, repoName: String, path: String, deleteVersion: String?) {
        //TODO SANPSHOT 按时间戳删除后，更新最新时间戳。
        //查找 `/groupId/artifactId/versionId/maven-metadata.xml`
        deleteVersion?.let {
            val versionMetadataNode = findMetadata(projectId, repoName, path)
            versionMetadataNode?.let { node ->
                storageService.load(
                    node.sha256!!,
                    Range.full(node.size),
                    ArtifactRemoveContext().storageCredentials
                ).use { artifactInputStream ->
                    //更新 `/groupId/artifactId/versionId/maven-metadata.xml`
                    val mavenMetadata = MetadataXpp3Reader().read(artifactInputStream)

                    val latestSnapshotVersion = findLatestSnapshotVersion(projectId, repoName, path)

                    val resultXml = mavenMetadata.toXmlString()
                    storeMetadataXml(resultXml, node)
                }
            }
        }
        //查找 `/groupId/artifactId/maven-metadata.xml`
        val artifactPath = path.substringBeforeLast("/")
        val artifactMetadataNode = findMetadata(projectId, repoName, artifactPath)
        artifactMetadataNode?.let { node ->
            storageService.load(
                node.sha256!!,
                Range.full(node.size),
                ArtifactRemoveContext().storageCredentials
            ).use { artifactInputStream ->
                //更新 `/groupId/artifactId/maven-metadata.xml`
                val mavenMetadata = MetadataXpp3Reader().read(artifactInputStream)
                mavenMetadata.versioning.release?.let {
                    mavenMetadata.versioning.release = mavenMetadata.versioning.versions.last()
                }
                val resultXml = mavenMetadata.toXmlString()
                storeMetadataXml(resultXml, node)
            }
        }
    }

    private fun findLatestSnapshotVersion(projectId: String, repoName: String, path: String): String? {
        val pathList = path.trim('/').split("/")
        val artifactId = path[pathList.size - 2]
        val versionId = path[pathList.size - 1]
        val nameRegex = "$artifactId-$versionId"
        val query = NodeQueryBuilder()
            .select("name")
            .sortByDesc("lastModifiedDate")
            .page(1, 1)
            .projectId(projectId)
            .repoName(repoName)
            .path("$path/")
            .excludeFolder()
            .name("*$nameRegex*", OperationType.MATCH)
            .build()

        val nodeList = nodeClient.search(query).data?.records
        nodeList?.let {
            val nodeName = it.first()["name"] as String
            val matcher = Pattern.compile(String.format(FILENAME_REGEX, artifactId)).matcher(nodeName)
            if (matcher.matches()) {
                return matcher.group(1)
            }
        }
        return null
    }

    private fun storeMetadataXml(resultXml: String, node: NodeDetail) {
        val resultXmlMd5 = resultXml.md5()
        val resultXmlSha1 = resultXml.sha1()

        val metadataArtifact = ByteArrayInputStream(resultXml.toByteArray()).use {
            ArtifactFileFactory.build(it)
        }
        val metadataArtifactMd5 = ByteArrayInputStream(resultXmlMd5.toByteArray()).use {
            ArtifactFileFactory.build(it)
        }
        val metadataArtifactSha1 = ByteArrayInputStream(resultXmlSha1.toByteArray()).use {
            ArtifactFileFactory.build(it)
        }

        logger.warn("${metadataArtifact.getSize()}")
        updateMetadata("${node.path}/maven-metadata.xml", metadataArtifact)
        metadataArtifact.delete()
        updateMetadata("${node.path}/maven-metadata.xml.md5", metadataArtifactMd5)
        metadataArtifactMd5.delete()
        updateMetadata("${node.path}/maven-metadata.xml.sha1", metadataArtifactSha1)
        metadataArtifactSha1.delete()
    }

    override fun query(context: ArtifactQueryContext): MavenArtifactVersionData? {
        val packageKey = context.request.getParameter("packageKey")
        val version = context.request.getParameter("version")
        val artifactId = packageKey.split(":").last()
        val groupId = packageKey.removePrefix("gav://").split(":")[0]
        val trueVersion = packageClient.findVersionByName(
            context.projectId,
            context.repoName,
            packageKey,
            version
        ).data ?: return null
        with(context.artifactInfo) {
            val jarNode = nodeClient.getNodeDetail(
                projectId, repoName, trueVersion.contentPath!!
            ).data ?: return null
            val stageTag = stageClient.query(projectId, repoName, packageKey, version).data
            val mavenArtifactMetadata = jarNode.metadata
            val packageVersion = packageClient.findVersionByName(
                projectId, repoName, packageKey, version
            ).data
            val count = packageVersion?.downloads ?: 0
            val mavenArtifactBasic = Basic(
                groupId,
                artifactId,
                version,
                jarNode.size, jarNode.fullPath,
                jarNode.createdBy, jarNode.createdDate,
                jarNode.lastModifiedBy, jarNode.lastModifiedDate,
                count,
                jarNode.sha256,
                jarNode.md5,
                stageTag,
                null
            )
            return MavenArtifactVersionData(mavenArtifactBasic, mavenArtifactMetadata)
        }
    }

    // maven 客户端下载统计
    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context) {
            val fullPath = artifactInfo.getArtifactFullPath()
            val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
            return if (node != null && node.metadata["packaging"] != null) {
                val mavenGAVC = fullPath.mavenGAVC()
                val version = mavenGAVC.version
                val artifactId = mavenGAVC.artifactId
                val groupId = mavenGAVC.groupId.formatSeparator("/", ".")
                val packageKey = PackageKeys.ofGav(groupId, artifactId)
                PackageDownloadRecord(projectId, repoName, packageKey, version)
            } else {
                null
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MavenLocalRepository::class.java)
    }
}
