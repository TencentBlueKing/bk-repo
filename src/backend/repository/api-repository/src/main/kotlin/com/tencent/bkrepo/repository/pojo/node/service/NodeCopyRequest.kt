package com.tencent.bkrepo.repository.pojo.node.service

import com.tencent.bkrepo.repository.pojo.ServiceRequest
import com.tencent.bkrepo.repository.pojo.node.CrossRepoNodeRequest
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 节点复制请求
 *
 * @author: carrypan
 * @date: 2019-10-15
 */
@ApiModel("节点复制请求")
data class NodeCopyRequest(
    @ApiModelProperty("源项目id", required = true)
    override val srcProjectId: String,
    @ApiModelProperty("源仓库名称", required = true)
    override val srcRepoName: String,
    @ApiModelProperty("源节点路径", required = true)
    override val srcFullPath: String,
    @ApiModelProperty("目的项目id", required = false)
    override val destProjectId: String? = null,
    @ApiModelProperty("目的仓库名称", required = false)
    override val destRepoName: String? = null,
    @ApiModelProperty("目的路径", required = true)
    override val destFullPath: String,
    @ApiModelProperty("同名文件是否覆盖", required = false)
    override val overwrite: Boolean = false,
    @ApiModelProperty("操作者", required = true)
    override val operator: String
) : CrossRepoNodeRequest, ServiceRequest {
    override fun getOperateName() = "Copy"
}
