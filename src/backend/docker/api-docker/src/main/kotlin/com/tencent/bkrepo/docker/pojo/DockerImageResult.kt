package com.tencent.bkrepo.docker.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("docker镜像信息查询结果信息")
data class DockerImageResult(
    @ApiModelProperty("totalRecords")
    var totalRecords: Int,
    @ApiModelProperty("records")
    var records: List<DockerImage>
)