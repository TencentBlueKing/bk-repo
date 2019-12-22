package com.tencent.bkrepo.repository.pojo.share

import com.tencent.bkrepo.repository.pojo.UserRequest
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("创建分享请求")
data class ShareRecordCreateRequest(
    @ApiModelProperty("分享用户")
    val authorizedUserList: List<String> = emptyList(),
    @ApiModelProperty("分享IP")
    val authorizedIpList: List<String> = emptyList(),
    @ApiModelProperty("有效时间，单位秒")
    val expireSeconds: Long = 0
) : UserRequest
