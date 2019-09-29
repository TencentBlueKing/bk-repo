package com.tencent.bkrepo.repository.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 更新节点请求
 *
 * @author: carrypan
 * @date: 2019-09-22
 */
@ApiModel("更新节点请求")
data class NodeUpdateRequest(
    @ApiModelProperty("修改者")
    val modifiedBy: String,
    @ApiModelProperty("路径")
    val path: String? = null,
    @ApiModelProperty("资源名称")
    val name: String? = null,
    @ApiModelProperty("文件大小，单位byte")
    val size: Long? = null,
    @ApiModelProperty("文件sha256")
    val sha256: String? = null,
    @ApiModelProperty("过期时间，单位天(0代表永久保存)")
    val expires: Long? = null
)
