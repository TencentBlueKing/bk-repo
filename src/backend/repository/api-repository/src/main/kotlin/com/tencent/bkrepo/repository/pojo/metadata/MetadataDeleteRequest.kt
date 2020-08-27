package com.tencent.bkrepo.repository.pojo.metadata

import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.ServiceRequest
import com.tencent.bkrepo.repository.pojo.node.NodeRequest
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 元数据删除请求
 */
@ApiModel("元数据删除请求")
data class MetadataDeleteRequest(
    @ApiModelProperty("项目id", required = true)
    override val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    override val repoName: String,
    @ApiModelProperty("节点完整路径", required = true)
    override val fullPath: String,
    @ApiModelProperty("待删除的元数据key列表", required = true)
    val keyList: Set<String>,
    @ApiModelProperty("操作用户")
    override val operator: String = SYSTEM_USER
) : NodeRequest, ServiceRequest
