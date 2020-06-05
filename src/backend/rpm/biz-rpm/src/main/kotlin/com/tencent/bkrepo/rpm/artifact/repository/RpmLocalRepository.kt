package com.tencent.bkrepo.rpm.artifact.repository

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.hash.sha256
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.rpm.util.GZipUtil.gZip
import com.tencent.bkrepo.rpm.util.UrlUtil.uriPattern
import com.tencent.bkrepo.rpm.util.redline.model.RpmRepoMd
import com.tencent.bkrepo.rpm.util.rpm.RpmFormatInterpreter
import com.tencent.bkrepo.rpm.util.rpm.RpmFormatReader
import groovy.lang.Script
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.FileInputStream

@Component
class RpmLocalRepository : LocalRepository() {

    @Autowired
    private lateinit var xmlScript: Script

    fun repodataNodeCreateRequest() {
    }

    fun xmlPrimaryNodeCreate(userId: String, repositoryInfo: RepositoryInfo, fullPath: String, xmlGZArtifact: ArtifactFile): NodeCreateRequest {
        val sha256 = xmlGZArtifact.getInputStream().sha256()
        val md5 = xmlGZArtifact.getInputStream().md5()
        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                fullPath = fullPath,
                size = xmlGZArtifact.getSize(),
                sha256 = sha256,
                md5 = md5,
                operator = userId
        )
    }

    override fun onUpload(context: ArtifactUploadContext) {
        val artifactFile = context.getArtifactFile()

        val rpmFormat = RpmFormatReader.wrapStreamAndRead(artifactFile.getInputStream())
        val rpmMetadata = RpmFormatInterpreter().interpret(rpmFormat)

        // uri 信息

        // rpm 包额外计算的信息
        artifactFile.getInputStream().sha1().let { rpmMetadata.sha1Digest = it }
        // artifactUri = /$releasever/os/$basearch/
        val uriParam = context.artifactInfo.artifactUri.uriPattern()
        val rpmArtifactName = with(rpmMetadata) { "$name-$version-$release.$architecture.rpm" }

        val artifactRelativePath = "${uriParam["type"]}/${uriParam["basearch"]}/$rpmArtifactName"
        rpmMetadata.artifactRelativePath = artifactRelativePath

        // repodata =
//        val location = context.artifactInfo.artifactUri
//        val result = xmlScript.invokeMethod("xmlWrapper", rpmMetadata)

        // todo 分布式锁
//        findXml()
//        updataXml()

        // first upload
        val xmlInitPackageString = xmlScript.invokeMethod("xmlPackageWrapper", rpmMetadata) as String
        val xmlInitPackageInputStream = (xmlInitPackageString.toByteArray()).let { ByteInputStream(it, it.size) }

        // xml文件sha1
        val xmlInitPackageSha1 = xmlInitPackageInputStream.sha1()
        // xml.gz文件sha1
        val xmlGZFile = xmlInitPackageInputStream.bytes.gZip()
        val xmlGZFileSha1 = FileInputStream(xmlGZFile).sha1()

        // 先保存primary-xml文件
        val xmlGZArtifact = ArtifactFileFactory.build(FileInputStream(xmlGZFile))
        val fullPath = "/${uriParam["releasever"]}/repodata/$xmlGZFileSha1-primary.xml.gz"
        val xmlPrimaryNode = xmlPrimaryNodeCreate(context.userId,
                context.repositoryInfo,
                fullPath,
                xmlGZArtifact)
        storageService.store(xmlPrimaryNode.sha256!!, xmlGZArtifact, context.storageCredentials)
        nodeResource.create(xmlPrimaryNode)

        // 保存repodata 节点
        val rpmRepoMd = RpmRepoMd(
                type = "primary",
                location = fullPath,
                size = xmlGZArtifact.getSize(),
                lastModified = System.currentTimeMillis().toString(),
                sha1 = xmlGZFileSha1
        )

        val xmlRepodataString = xmlScript.invokeMethod("wrapperRepomd", listOf(rpmRepoMd)) as String
        val xmlRepodataInputStream = (xmlRepodataString.toByteArray()).let { ByteInputStream(it, it.size) }
        val xmlRepodataArtifact = ArtifactFileFactory.build(xmlRepodataInputStream)
        val xmlRepomdNode = xmlPrimaryNodeCreate(context.userId,
                context.repositoryInfo,
                "/${uriParam["releasever"]}/repodata/repomd.xml",
                xmlRepodataArtifact)
        storageService.store(xmlRepomdNode.sha256!!, xmlRepodataArtifact, context.storageCredentials)
        nodeResource.create(xmlRepomdNode)

        // 保存rpm 包
        val nodeCreateRequest = getNodeCreateRequest(context)
        storageService.store(nodeCreateRequest.sha256!!, artifactFile, context.storageCredentials)
        nodeResource.create(nodeCreateRequest)
    }
}
