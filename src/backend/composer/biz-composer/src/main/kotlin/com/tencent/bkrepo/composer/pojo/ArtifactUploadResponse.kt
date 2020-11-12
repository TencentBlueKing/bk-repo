package com.tencent.bkrepo.composer.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("构件 upload成功时返回信息")
data class ArtifactUploadResponse(
    @ApiModelProperty("项目")
    val projectId: String,
    @ApiModelProperty("仓库")
    val repoName: String,
    @ApiModelProperty("构件uri")
    val artifactUri: String,
    @ApiModelProperty("构件sha256")
    val sha256: String,
    @ApiModelProperty("构件md5")
    val md5: String,
    @ApiModelProperty("上传结果描述")
    val description: String
)
