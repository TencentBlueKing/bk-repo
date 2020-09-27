package com.tencent.bkrepo.docker.pojo

import io.swagger.annotations.ApiModelProperty

data class DockerTagResult(
    @ApiModelProperty("totalRecords")
    var totalRecords: Long,
    @ApiModelProperty("records")
    var records: List<DockerTag>
)
