package com.tencent.bkrepo.nuget.pojo.response.search

data class NugetSearchResponse(
    val totalHits: Int = 0,
    val data: List<SearchResponseData> = emptyList()
)
