package com.tencent.bkrepo.npm.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("npm error返回包装模型")
data class NpmErrorResponse(
    @ApiModelProperty("返回状态")
    val error: String,
    @ApiModelProperty("错误信息")
    val reason: String
) {
    companion object {
        fun notFound() = NpmErrorResponse("not_found", "document not found")
    }
}
