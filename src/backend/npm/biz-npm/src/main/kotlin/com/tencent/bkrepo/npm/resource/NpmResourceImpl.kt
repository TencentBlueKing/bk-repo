package com.tencent.bkrepo.npm.resource

import com.tencent.bkrepo.npm.api.NpmResource
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.exception.NpmArtifactNotFoundException
import com.tencent.bkrepo.npm.pojo.NpmDeleteResponse
import com.tencent.bkrepo.npm.pojo.NpmSearchResponse
import com.tencent.bkrepo.npm.pojo.NpmSuccessResponse
import com.tencent.bkrepo.npm.pojo.metadata.MetadataSearchRequest
import com.tencent.bkrepo.npm.service.NpmService
import com.tencent.bkrepo.npm.utils.GsonUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class NpmResourceImpl : NpmResource {

    @Autowired
    private lateinit var npmService: NpmService

    override fun publish(userId: String, artifactInfo: NpmArtifactInfo, body: String): NpmSuccessResponse {
        return npmService.publish(userId, artifactInfo, body)
    }

    override fun searchPackageInfo(artifactInfo: NpmArtifactInfo): Map<String, Any> {
        val fileInfo = npmService.searchPackageInfo(artifactInfo)
        return fileInfo?.let { GsonUtils.gsonToMaps<Any>(it) }
            ?: throw NpmArtifactNotFoundException("document not found")
    }

    override fun download(artifactInfo: NpmArtifactInfo) {
        npmService.download(artifactInfo)
    }

    override fun unpublish(userId: String, artifactInfo: NpmArtifactInfo): NpmDeleteResponse {
        return npmService.unpublish(userId, artifactInfo)
    }

    override fun updatePkg(artifactInfo: NpmArtifactInfo, body: String): NpmSuccessResponse {
        return npmService.updatePkg(artifactInfo, body)
    }

    override fun unPublishPkgWithVersion(
        artifactInfo: NpmArtifactInfo,
        scope: String?,
        name: String,
        delimiter: String,
        filename: String,
        rev: String
    ): NpmDeleteResponse {
        val pkgName = scope?.let { String.format("%s/%s",it,name) } ?: name
        return npmService.unPublishPkgWithVersion(artifactInfo, pkgName, delimiter, filename, rev)
    }

    override fun search(artifactInfo: NpmArtifactInfo, searchRequest: MetadataSearchRequest): NpmSearchResponse {
        return npmService.search(artifactInfo, searchRequest)
    }

    override fun getDistTagsInfo(artifactInfo: NpmArtifactInfo): Map<String, String> {
        return npmService.getDistTagsInfo(artifactInfo)
    }

    override fun addDistTags(artifactInfo: NpmArtifactInfo, body: String): NpmSuccessResponse {
        return npmService.addDistTags(artifactInfo, body)
    }

    override fun deleteDistTags(artifactInfo: NpmArtifactInfo) {
        return npmService.deleteDistTags(artifactInfo)
    }
}
