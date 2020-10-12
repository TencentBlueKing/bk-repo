package com.tencent.bkrepo.dockerapi.pojo

data class QueryProjectImageRequest(
    val searchKey: String?,
    val projectId: String,
    val repoName: String?,
    override var page: Int = 0,
    override var pageSize: Int = 20
) : PageRequest(page, pageSize)