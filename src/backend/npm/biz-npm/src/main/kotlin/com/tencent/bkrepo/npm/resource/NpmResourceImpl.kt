package com.tencent.bkrepo.npm.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.npm.api.NpmResource
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.service.NpmService
import com.tencent.bkrepo.npm.utils.GsonUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class NpmResourceImpl : NpmResource {

    @Autowired
    private lateinit var npmService: NpmService

    override fun publish(userId: String, artifactInfo: NpmArtifactInfo, body: String): Response<Void> {
        npmService.publish(userId, artifactInfo, body)
        return ResponseBuilder.success()
    }

    override fun searchPackageInfo(artifactInfo: NpmArtifactInfo): Map<String, Any> {
        val fileInfo = npmService.searchPackageInfo(artifactInfo)
        val errorMap = mapOf(Pair("error", "not_found"), Pair("reason", "document not found"))
        val gsonToMaps = fileInfo?.let { GsonUtils.gsonToMaps<Any>(it) }
        return gsonToMaps ?: errorMap
    }

    override fun download(artifactInfo: NpmArtifactInfo) {
        npmService.download(artifactInfo)
    }

    override fun unpublish(userId: String, artifactInfo: NpmArtifactInfo): Response<Void> {
        npmService.unpublish(userId, artifactInfo)
        return ResponseBuilder.success()
    }
}
