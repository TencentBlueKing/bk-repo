package com.tencent.bkrepo.dockeradapter.pojo

data class QueryProjectImageRequest(
    val searchKey: String? = "",
    val projectId: String,
    val repoName: String? = "",
    override var page: Int = 0,
    override var pageSize: Int = 0
) : PageRequest(page, pageSize)