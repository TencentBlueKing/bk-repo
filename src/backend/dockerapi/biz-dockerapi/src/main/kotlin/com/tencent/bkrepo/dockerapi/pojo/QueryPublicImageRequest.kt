package com.tencent.bkrepo.dockerapi.pojo

data class QueryPublicImageRequest(
    val searchKey: String? = "",
    override var page: Int = 0,
    override var pageSize: Int = 0
) : PageRequest(page, pageSize)