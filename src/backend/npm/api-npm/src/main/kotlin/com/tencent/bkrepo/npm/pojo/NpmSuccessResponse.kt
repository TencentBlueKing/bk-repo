package com.tencent.bkrepo.npm.pojo

import io.swagger.annotations.Api

@Api("npm success返回包装模型")
data class NpmSuccessResponse(
    val ok: String
) {
    companion object {
        fun createEntitySuccess() = NpmSuccessResponse("created new entity")
        fun createTagSuccess() = NpmSuccessResponse("created new tag")
        fun updatePkgSuccess() = NpmSuccessResponse("update package")
    }
}
