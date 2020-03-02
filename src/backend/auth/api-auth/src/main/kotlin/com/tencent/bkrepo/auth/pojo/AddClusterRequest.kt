package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("新增集群请求")
data class AddClusterRequest(
    @ApiModelProperty("集群id")
    val clusterId: String,
    @ApiModelProperty("集群地址")
    val clusterAddr: String,
    @ApiModelProperty("集群认证状态")
    val credentialStatus: Boolean? = false
)
