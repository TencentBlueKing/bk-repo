package com.tencent.bkrepo.dockeradapter.pojo

data class QueryImageTagRequest(
    val projectId: String,
    val repoName: String?,
    val imageRepo: String,
    override var page: Int = 0,
    override var pageSize: Int = 0
) : PageRequest(page, pageSize)