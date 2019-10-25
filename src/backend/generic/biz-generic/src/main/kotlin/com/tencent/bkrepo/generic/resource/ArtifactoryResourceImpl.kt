package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.api.ArtifactoryResource
import com.tencent.bkrepo.generic.pojo.artifactory.JfrogFilesData
import com.tencent.bkrepo.generic.service.ArtifactoryService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class ArtifactoryResourceImpl @Autowired constructor(
    private val artifactoryService: ArtifactoryService
) : ArtifactoryResource {
    override fun upload(userId: String, projectId: String, repoName: String, fullPath: String, request: HttpServletRequest): Response<Void> {
        artifactoryService.upload(userId, projectId, repoName, fullPath, parseMetaData(request.requestURI), request)
        return Response.success()
    }

    override fun download(userId: String, projectId: String, repoName: String, fullPath: String, response: HttpServletResponse) {
        artifactoryService.download(userId, projectId, repoName, fullPath, response)
    }

    override fun listFile(userId: String, projectId: String, repoName: String, fullPath: String, response: HttpServletResponse): JfrogFilesData {
        return artifactoryService.listFile(userId, projectId, repoName, fullPath, includeFolder = true, deep = true)
    }

    private fun parseMetaData(fullPath: String): Map<String, String> {
        val splits = fullPath.split(";")
        val metadataMap = mutableMapOf<String, String>()

        if (splits.size > 1) {
            for (i in 1 until splits.size) {
                val metadata = parseKeyAndValue(splits[i]) ?: continue
                metadataMap[metadata.first] = metadata.second
            }
        }
        return metadataMap
    }

    private fun parseKeyAndValue(str: String): Pair<String, String>? {
        val splits = str.split("=")
        return if (splits.size == 2) {
            Pair(splits[0], splits[1])
        } else {
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }
}