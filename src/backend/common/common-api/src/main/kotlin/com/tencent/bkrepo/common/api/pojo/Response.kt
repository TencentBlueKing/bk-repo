package com.tencent.bkrepo.common.api.pojo

import com.fasterxml.jackson.annotation.JsonIgnore
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("数据返回包装模型")
data class Response<out T>(
    @ApiModelProperty("返回码", required = true)
    val code: Int,
    @ApiModelProperty("错误信息", required = false)
    val message: String? = null,
    @ApiModelProperty("数据", required = false)
    val data: T? = null
) {
    constructor(data: T) : this(CommonMessageCode.SUCCESS, null, data)
    constructor(code: Int, message: String) : this(code, message, null)

    @JsonIgnore
    fun isOk(): Boolean {
        return code == CommonMessageCode.SUCCESS
    }

    @JsonIgnore
    fun isNotOk(): Boolean {
        return code != CommonMessageCode.SUCCESS
    }
}
