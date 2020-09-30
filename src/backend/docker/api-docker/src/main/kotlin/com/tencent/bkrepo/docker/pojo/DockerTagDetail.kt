package com.tencent.bkrepo.docker.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("docker镜像tag信息")
data class DockerTagDetail(
    @ApiModelProperty("basic")
    val basic: Map<String, Any>,
    @ApiModelProperty("history")
    val history: List<Any>,
    @ApiModelProperty("metadata")
    val metadata: Map<String, Any>,
    @ApiModelProperty("layers")
    val layers: List<Any>
)
