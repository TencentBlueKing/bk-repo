package com.tencent.bkrepo.helm.pojo

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.Api
import io.swagger.annotations.ApiModelProperty

@Api("helm success 包装模型")
data class HelmSuccessResponse(
    @ApiModelProperty("是否成功")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val saved: Boolean?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val deleted: Boolean?
) {
    companion object {
        fun pushSuccess() = HelmSuccessResponse(true, null)
        fun deleteSuccess() = HelmSuccessResponse(null, true)
    }
}
