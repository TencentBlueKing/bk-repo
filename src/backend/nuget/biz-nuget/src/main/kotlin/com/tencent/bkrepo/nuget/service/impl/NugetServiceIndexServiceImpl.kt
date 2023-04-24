package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.constant.NugetQueryType
import com.tencent.bkrepo.nuget.constant.QUERY_TYPE
import com.tencent.bkrepo.nuget.pojo.v3.metadata.feed.Feed
import com.tencent.bkrepo.nuget.service.NugetServiceIndexService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NugetServiceIndexServiceImpl : NugetServiceIndexService {
    override fun getFeed(artifactInfo: NugetArtifactInfo): Feed {
        val repository = ArtifactContextHolder.getRepository()
        val context = ArtifactQueryContext()
        context.putAttribute(QUERY_TYPE, NugetQueryType.SERVICE_INDEX)
        return repository.query(context) as Feed
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NugetServiceIndexServiceImpl::class.java)
    }
}
