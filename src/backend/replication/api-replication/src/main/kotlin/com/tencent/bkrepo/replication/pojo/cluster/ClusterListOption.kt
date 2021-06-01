package com.tencent.bkrepo.replication.pojo.cluster

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("节点列表选项")
class ClusterListOption(
    @ApiModelProperty("当前页")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @ApiModelProperty("分页大小")
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    @ApiModelProperty("节点名称, 根据该字段模糊搜索")
    val name: String? = null,
    @ApiModelProperty("节点类型")
    val type: ClusterNodeType? = null
)
