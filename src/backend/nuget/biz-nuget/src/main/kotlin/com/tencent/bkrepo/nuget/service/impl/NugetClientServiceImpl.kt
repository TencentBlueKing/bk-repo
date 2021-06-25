package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.model.v2.search.NuGetSearchRequest
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDeleteArtifactInfo
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDownloadArtifactInfo
import com.tencent.bkrepo.nuget.pojo.artifact.NugetPublishArtifactInfo
import com.tencent.bkrepo.nuget.service.NugetClientService
import com.tencent.bkrepo.nuget.util.NugetUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class NugetClientServiceImpl : NugetClientService, ArtifactService() {

    override fun getServiceDocument(artifactInfo: NugetArtifactInfo) {
        val response = HttpContextHolder.getResponse()
        try {
            var serviceDocument = NugetUtils.getServiceDocumentResource()
            serviceDocument = serviceDocument.replace(
                "\$\$baseUrl\$\$",
                HttpContextHolder.getRequest().requestURL.toString()
            )
            response.contentType = MediaTypes.APPLICATION_XML
            response.writer.write(serviceDocument)
        } catch (exception: IOException) {
            logger.error("unable to read resource: $exception")
            throw exception
        }
    }

    override fun publish(userId: String, publishInfo: NugetPublishArtifactInfo) {
        logger.info("user [$userId] handling publish package request in repo [${publishInfo.getRepoIdentify()}]")
        val context = ArtifactUploadContext(publishInfo.artifactFile)
        repository.upload(context)
        logger.info(
            "user [$userId] publish nuget package [${publishInfo.nuspecPackage.metadata.id}] with version " +
                "[${publishInfo.nuspecPackage.metadata.version}] success to repo [${publishInfo.getRepoIdentify()}]"
        )
        context.response.status = HttpStatus.CREATED.value
        context.response.writer.write("Successfully published NuPkg to: ${publishInfo.getArtifactFullPath()}")
    }

    override fun download(userId: String, artifactInfo: NugetDownloadArtifactInfo) {
        repository.download(ArtifactDownloadContext())
    }

    override fun findPackagesById(artifactInfo: NugetArtifactInfo, searchRequest: NuGetSearchRequest) {
        // todo
    }

    override fun delete(userId: String, artifactInfo: NugetDeleteArtifactInfo) {
        with(artifactInfo) {
            logger.info("handling delete package version request for package [$packageName] and version [$version] " +
                "in repo [${artifactInfo.getRepoIdentify()}]")
            ArtifactContextHolder.getRepository().remove(ArtifactRemoveContext())
            logger.info("userId [$userId] delete version [$version] for package [$packageName] " +
                "in repo [${this.getRepoIdentify()}] success.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NugetClientServiceImpl::class.java)
    }
}
