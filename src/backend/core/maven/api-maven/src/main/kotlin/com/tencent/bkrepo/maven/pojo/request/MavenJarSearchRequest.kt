package com.tencent.bkrepo.maven.pojo.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "根据gav搜索jar相关信息")
data class MavenJarSearchRequest(
    @get:Schema(title = "文件名")
    val fileList: List<String>,
)
