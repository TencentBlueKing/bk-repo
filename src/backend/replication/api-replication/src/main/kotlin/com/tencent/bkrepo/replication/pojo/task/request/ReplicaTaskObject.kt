package com.tencent.bkrepo.replication.pojo.task.request

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("同步任务对象信息创建请求")
data class ReplicaTaskObject(
    @ApiModelProperty("本地仓库", required = true)
    val localRepoName: String,
    @ApiModelProperty("远程仓库", required = true)
    val remoteRepoName: String,
    @ApiModelProperty("仓库类型", required = true)
    val repoType: RepositoryType,
    @ApiModelProperty("包限制条件", required = false)
    val packageConstraints: List<PackageConstraint>? = null,
    @ApiModelProperty("路径限制条件，包限制和路径限制都为空则同步整个仓库数据", required = false)
    val pathConstraints: List<PathConstraint>? = null
)
