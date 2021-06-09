package com.tencent.bkrepo.replication.pojo.record

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("执行日志详情列表选项")
class ReplicaRecordDetailListOption(
    @ApiModelProperty("当前页")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @ApiModelProperty("分页大小")
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    @ApiModelProperty("包名称, 根据该字段模糊搜索")
    val packageName: String? = null,
    @ApiModelProperty("仓库名称")
    val repoName: String? = null,
    @ApiModelProperty("远程节点名称")
    val clusterName: String? = null,
    @ApiModelProperty("路径名称, 根据该字段前缀匹配")
    val path: String? = null,
    @ApiModelProperty("执行状态")
    val status: ExecutionStatus? = null
)
