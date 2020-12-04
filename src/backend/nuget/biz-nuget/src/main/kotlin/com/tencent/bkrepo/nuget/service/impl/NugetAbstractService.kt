package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.common.api.constant.CharPool.SLASH
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.service.util.HttpContextHolder

abstract class NugetAbstractService {
    fun getV2Url(artifactInfo: ArtifactInfo): String {
        val url = HttpContextHolder.getRequest().requestURL
        val domain = url.delete(url.length - HttpContextHolder.getRequest().requestURI.length, url.length)
        // return domain.append(SLASH).append("nuget").append(SLASH).append(artifactInfo.getRepoIdentify()).toString()
        return domain.append(SLASH).append(artifactInfo.getRepoIdentify()).toString()
    }

    fun getV3Url(artifactInfo: ArtifactInfo): String {
        val url = HttpContextHolder.getRequest().requestURL
        val domain = url.delete(url.length - HttpContextHolder.getRequest().requestURI.length, url.length)
        return domain.append(SLASH).append("v3").append(SLASH).append(artifactInfo.getRepoIdentify()).toString()
    }
}