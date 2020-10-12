package com.tencent.bkrepo.dockerapi.pojo

data class QueryProjectImageRequest(
    val searchKey: String?,
    val projectId: String,
    val repoName: String?,
    override var pageNumber: Int = 0,
    override var pageSize: Int = 20
) : PageRequest(pageNumber, pageSize)