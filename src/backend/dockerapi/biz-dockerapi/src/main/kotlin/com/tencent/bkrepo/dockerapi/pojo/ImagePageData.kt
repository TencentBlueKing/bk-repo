package com.tencent.bkrepo.dockerapi.pojo

data class ImagePageData(
    val imageList: List<DockerRepo>,
    val page: Int,
    val pageSize: Int,
    val total: Int
)