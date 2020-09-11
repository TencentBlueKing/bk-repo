package com.tencent.bkrepo.docker.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("docker镜像信息")
data class DockerImage(
    @ApiModelProperty("name")
    val name: String,
    @ApiModelProperty("最后修改人")
    val lastModifiedBy: String,
    @ApiModelProperty("最后修改时间")
    val lastModifiedDate: String,
    @ApiModelProperty("下载次数")
    val downloadCount: Long,
    @ApiModelProperty("镜像logo地址")
    val logoUrl: String,
    @ApiModelProperty("镜像描述")
    val description: String
)
