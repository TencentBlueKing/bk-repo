package com.tencent.bkrepo.repository.pojo.share

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("分享记录信息")
data class ShareRecordInfo(
    @ApiModelProperty("项目")
    val projectId: String,
    @ApiModelProperty("仓库")
    val repoName: String,
    @ApiModelProperty("完整路径")
    val fullPath: String,
    @ApiModelProperty("分享url")
    val shareUrl: String,
    @ApiModelProperty("授权用户")
    val authorizedUserList: List<String>,
    @ApiModelProperty("授权IP")
    val authorizedIpList: List<String>,
    @ApiModelProperty("过期时间")
    val expireDate: String?
)