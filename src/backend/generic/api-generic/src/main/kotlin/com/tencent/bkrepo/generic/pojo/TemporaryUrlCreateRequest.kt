package com.tencent.bkrepo.generic.pojo

import com.tencent.bkrepo.repository.pojo.token.TokenType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.Duration

@ApiModel("创建临时访问url请求")
data class TemporaryUrlCreateRequest(
    @ApiModelProperty("项目id")
    val projectId: String,
    @ApiModelProperty("仓库名称")
    val repoName: String,
    @ApiModelProperty("授权路径列表")
    val fullPathSet: Set<String>,
    @ApiModelProperty("授权用户")
    val authorizedUserSet: Set<String> = emptySet(),
    @ApiModelProperty("授权IP")
    val authorizedIpSet: Set<String> = emptySet(),
    @ApiModelProperty("有效时间，单位秒")
    val expireSeconds: Long = Duration.ofDays(1).seconds,
    @ApiModelProperty("允许访问次数，为空表示无限制")
    val permits: Int? = null,
    @ApiModelProperty("token类型")
    val type: TokenType,
    @ApiModelProperty("指定临时访问链接host")
    val host: String? = null,
    @ApiModelProperty("是否通知用户")
    val needsNotify: Boolean = false
)

