package com.tencent.bkrepo.repository.pojo.node.user

import com.tencent.bkrepo.repository.pojo.UserRequest
import com.tencent.bkrepo.repository.pojo.node.CrossRepoNodeRequest
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 用户节点复制请求
 */
@ApiModel("用户节点复制请求")
data class UserNodeCopyRequest(
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
    @Deprecated("This property is deprecated!", ReplaceWith("destFullPath"))
    @ApiModelProperty("目的路径", required = true)
    val destPath: String? = null,
    @ApiModelProperty("同名文件是否覆盖", required = false)
    override val overwrite: Boolean = false
) : CrossRepoNodeRequest, UserRequest {
    override fun getOperateName() = "Copy"
}
