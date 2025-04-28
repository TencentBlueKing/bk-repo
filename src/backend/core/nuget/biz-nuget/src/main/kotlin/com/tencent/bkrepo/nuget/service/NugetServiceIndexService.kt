package com.tencent.bkrepo.nuget.service

import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.pojo.v3.metadata.feed.Feed

interface NugetServiceIndexService {

    /**
     * 获取index.json内容
     */
    fun getFeed(artifactInfo: NugetArtifactInfo): Feed
}
