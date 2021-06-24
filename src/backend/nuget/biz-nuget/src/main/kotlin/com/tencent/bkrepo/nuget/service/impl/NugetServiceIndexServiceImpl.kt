package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.service.NugetServiceIndexService
import com.tencent.bkrepo.nuget.util.NugetUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class NugetServiceIndexServiceImpl : NugetServiceIndexService {
    override fun getFeed(artifactInfo: NugetArtifactInfo) {
        val response = HttpContextHolder.getResponse()
        return try {
            var feedResource = NugetUtils.getFeedResource()
            feedResource = feedResource.replace(
                "@NugetV2Url", NugetUtils.getV2Url(artifactInfo)
            ).replace(
                "@NugetV3Url", NugetUtils.getV3Url(artifactInfo)
            )
            response.contentType = MediaTypes.APPLICATION_JSON
            response.writer.write(feedResource)
        } catch (exception: IOException) {
            logger.error("unable to read resource: $exception")
            throw exception
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NugetServiceIndexServiceImpl::class.java)
    }
}
