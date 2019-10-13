package com.tencent.bkrepo.generic.pojo.operate

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

/**
 * 文件搜索请求
 *
 * @author: carrypan
 * @date: 2019-10-13
 */
@ApiModel("文件搜索请求")
data class FileSearchRequest(
        @ApiModelProperty("匹配路径列表", required = true)
        private val pathPattern: List<String>,
        @ApiModelProperty("元数据匹配条件", required = true)
        private val metadataCondition: Map<String, String>
)