package com.tencent.bkrepo.common.api.pojo

import com.fasterxml.jackson.annotation.JsonIgnore
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("数据返回包装模型")
data class Response<out T>(
    @ApiModelProperty("返回码")
    val code: Int,
    @ApiModelProperty("错误信息")
    val message: String? = null,
    @ApiModelProperty("数据")
    val data: T? = null,
    @ApiModelProperty("链路追踪id")
    val traceId: String
) {
    @JsonIgnore
    fun isOk(): Boolean {
        return code == CommonMessageCode.SUCCESS.getCode()
    }

    @JsonIgnore
    fun isNotOk(): Boolean {
        return code != CommonMessageCode.SUCCESS.getCode()
    }
}
