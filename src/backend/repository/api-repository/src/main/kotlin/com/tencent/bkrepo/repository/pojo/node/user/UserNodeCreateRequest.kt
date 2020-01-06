package com.tencent.bkrepo.repository.pojo.node.user

import com.tencent.bkrepo.repository.pojo.UserRequest
import com.tencent.bkrepo.repository.pojo.node.NodeRequest
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 创建节点请求
 *
 * @author: carrypan
 * @date: 2019-09-22
 */
@ApiModel("创建节点请求")
data class UserNodeCreateRequest(
    @ApiModelProperty("所属项目", required = true)
    override val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    override val repoName: String,
    @ApiModelProperty("完整路径", required = true)
    override val fullPath: String,
    @ApiModelProperty("是否为文件夹", required = true)
    val folder: Boolean,
    @ApiModelProperty("过期时间，单位天(0代表永久保存)")
    val expires: Long = 0,
    @ApiModelProperty("是否覆盖")
    val overwrite: Boolean = false,
    @ApiModelProperty("文件大小，单位byte")
    val size: Long? = null,
    @ApiModelProperty("文件sha256")
    val sha256: String? = null,
    @ApiModelProperty("文件md5")
    val md5: String? = null,
    @ApiModelProperty("元数据信息")
    val metadata: Map<String, String>? = null
) : NodeRequest, UserRequest
