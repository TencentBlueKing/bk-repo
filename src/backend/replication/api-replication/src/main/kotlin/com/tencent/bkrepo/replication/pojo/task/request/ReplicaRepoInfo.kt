package com.tencent.bkrepo.replication.pojo.task.request

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("同步任务创建请求仓库信息")
data class ReplicaRepoInfo(
    @ApiModelProperty("所属仓库名称", required = true)
    val repoName: String,
    @ApiModelProperty("远程仓库名称", required = true)
    val remoteRepoName: String,
    @ApiModelProperty("所属仓库类型", required = true)
    val repoType: RepositoryType
)
