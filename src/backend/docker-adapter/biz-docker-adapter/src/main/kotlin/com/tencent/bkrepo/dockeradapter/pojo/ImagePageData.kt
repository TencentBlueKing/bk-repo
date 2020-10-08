package com.tencent.bkrepo.dockeradapter.pojo

import com.tencent.bkrepo.dockeradapter.pojo.DockerRepo

data class ImagePageData(
    val imageList: List<DockerRepo>,
    val page: Int,
    val pageSize: Int,
    val total: Int
)