package com.tencent.bkrepo.dockerapi.pojo

data class QueryImageTagRequest(
    val projectId: String,
    val repoName: String?,
    val imageRepo: String,
    override var pageNumber: Int = 0,
    override var pageSize: Int = 0
) : PageRequest(pageNumber, pageSize)