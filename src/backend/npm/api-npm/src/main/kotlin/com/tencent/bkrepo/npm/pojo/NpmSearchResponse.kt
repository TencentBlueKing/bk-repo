package com.tencent.bkrepo.npm.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("npm search 返回格式封装")
@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
data class NpmSearchResponse(
    @ApiModelProperty("对应数据")
    var objects: List<NpmSearchInfoMap> = emptyList()
)

data class NpmSearchInfoMap(
    var `package`: NpmSearchInfo? = null
)

data class NpmSearchInfo(
    var name: String? = null,
    var description: String? = null,
    var maintainers: List<Map<String,Any>> = emptyList(),
    var version: String? = null,
    var date: String? = null,
    var keywords: List<String> = emptyList(),
    var author: Map<String, Any>? = emptyMap()
)
