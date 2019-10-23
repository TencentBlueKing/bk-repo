package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.api.ArtifactoryResource
import com.tencent.bkrepo.generic.service.ArtifactoryService
import com.tencent.bkrepo.generic.service.OperateService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class ArtifactoryResourceImpl @Autowired constructor(
    private val artifactoryService: ArtifactoryService
) : ArtifactoryResource {
    override fun upload(userId: String, projectId: String, repoName: String, fullPath: String, request: HttpServletRequest): Response<Void> {
        val pathAndMetaData = parsePathAndMetaData(fullPath)
        artifactoryService.upload(userId, projectId, repoName, pathAndMetaData.first, pathAndMetaData.second, request)
        return Response.success()
    }

    override fun download(userId: String, projectId: String, repoName: String, fullPath: String, response: HttpServletResponse) {
        artifactoryService.download(userId, projectId, repoName, fullPath, response)
    }

    override fun listFile(userId: String, projectId: String, repoName: String, fullPath: String, response: HttpServletResponse) {
        val data = artifactoryService.listFile(userId, projectId, repoName, fullPath, includeFolder = true, deep = true)
        response
    }

    private fun parsePathAndMetaData(fullPath: String): Pair<String, Map<String, String>>{
        val splits = fullPath.split(";")
        val path = splits[0]
        val metadataMap = mutableMapOf<String, String>()
        if(splits.size > 2){
            for(i in 1..splits.size){
                val metadata = parseKeyAndValue(splits[i]) ?: continue
                metadataMap[metadata.first] = metadata.second
            }
        }
        return Pair(path, metadataMap)
    }

    private fun parseKeyAndValue(str: String): Pair<String, String>? {
        val splits = str.split("=")
        return if(splits.size == 2){
            Pair(splits[0], splits[1])
        } else {
            null
        }
    }
}
