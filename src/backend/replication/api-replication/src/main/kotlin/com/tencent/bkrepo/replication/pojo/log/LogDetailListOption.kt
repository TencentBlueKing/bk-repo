package com.tencent.bkrepo.replication.pojo.log

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("日志详情列表选项")
data class LogDetailListOption(
    @ApiModelProperty("当前页")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @ApiModelProperty("分页大小")
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    @ApiModelProperty("制品名称")
    val packageName: String? = null,
    @ApiModelProperty("从节点")
    val slaveName: String? = null,
    @ApiModelProperty("状态")
    val status: String? = null,
    @ApiModelProperty("仓库名称")
    val repoName: String? = null
)
