package com.tencent.bkrepo.repository.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 创建节点请求
 *
 * @author: carrypan
 * @date: 2019-09-22
 */
@ApiModel("创建节点请求")
data class NodeCreateRequest(
    @ApiModelProperty("是否为文件夹")
    val folder: Boolean,
    @ApiModelProperty("路径")
    val path: String,
    @ApiModelProperty("资源名称")
    val name: String,
    @ApiModelProperty("所属仓库id")
    val repositoryId: String,
    @ApiModelProperty("创建者")
    val createdBy: String,
    @ApiModelProperty("文件过期时间,单位秒。小于等于0则永久存放")
    val expired: Long = 0,
    @ApiModelProperty("文件大小，单位byte")
    val size: Long? = null,
    @ApiModelProperty("文件sha256")
    val sha256: String? = null,
    @ApiModelProperty("分块信息列表")
    val blockList: List<FileBlock>? = null

)
