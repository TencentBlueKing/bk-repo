package com.tencent.bkrepo.docker.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("docker镜像tag信息")
data class DockerTag(
    @ApiModelProperty("tag")
    val tag: String,
    @ApiModelProperty("stageTag")
    val stageTag: String,
    @ApiModelProperty("大小")
    val size: Int,
    @ApiModelProperty("最后修改人")
    val lastModifiedBy: String,
    @ApiModelProperty("最后修改时间")
    val lastModifiedDate: String,
    @ApiModelProperty("下载次数")
    val downloadCount: Long
)
