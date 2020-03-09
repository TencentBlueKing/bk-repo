package com.tencent.bkrepo.npm.pojo.auth

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("npm auth返回包装模型")
data class NpmAuthResponse<out T>(
    @ApiModelProperty("返回状态")
    val ok: Boolean,
    @ApiModelProperty("错误信息")
    val id: String? = null,
    @ApiModelProperty("数据")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val token: T? = null
) {
    constructor(ok: Boolean, id: String) : this(ok, id, null)

    companion object {
        fun success() = NpmAuthResponse(true, null, null)

        fun <T> success(data: T) = NpmAuthResponse(true, null, data)

        fun <T> success(id: String, data: T) = NpmAuthResponse(true, id, data)

        fun fail(id: String) = NpmAuthResponse(false, id, null)
    }
}
