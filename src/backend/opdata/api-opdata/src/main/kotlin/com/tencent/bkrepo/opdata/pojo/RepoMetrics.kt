package com.tencent.bkrepo.opdata.pojo

import io.swagger.annotations.ApiModelProperty

class RepoMetrics(
    @ApiModelProperty("repoName")
    val repoName: String,
    @ApiModelProperty("size")
    val size: Long,
    @ApiModelProperty("num")
    val num: Long
)
