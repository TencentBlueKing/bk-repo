package com.tencent.bkrepo.nuget.service

import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.model.v3.search.SearchRequest
import com.tencent.bkrepo.nuget.model.v3.search.SearchResponse

interface NugetV3PackageService {

    /**
     * 根据[searchRequest]里面的条件进行搜索
     */
    fun search(artifactInfo: NugetArtifactInfo, searchRequest: SearchRequest): SearchResponse
}
