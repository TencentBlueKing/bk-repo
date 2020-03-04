package com.tencent.bkrepo.auth.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("集群信息")
data class Cluster(
    @ApiModelProperty("集群id")
    val clusterId: String,
    @ApiModelProperty("集群地址")
    val clusterAddr: String,
    @ApiModelProperty("集群证书")
    val cert: String,
    @ApiModelProperty("集群认证状态")
    val credentialStatus: Boolean? = false
)
