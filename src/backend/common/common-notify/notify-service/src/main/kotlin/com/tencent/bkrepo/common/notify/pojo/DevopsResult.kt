package com.tencent.bkrepo.common.notify.pojo

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("蓝盾api调用结果")
data class DevopsResult<out T>(
    @ApiModelProperty("返回码")
    val status: Int,
    @ApiModelProperty("错误信息")
    val message: String? = null,
    @ApiModelProperty("数据")
    val data: T? = null
) {
    @JsonIgnore
    fun isOk(): Boolean {
        return status == 0
    }

    @JsonIgnore
    fun isNotOk(): Boolean {
        return status != 0
    }
}
