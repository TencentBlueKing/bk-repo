package com.tencent.bkrepo.oci.pojo.response

data class OciResponse<T>(
    val errors: List<T>
) {
    companion object {
        fun unAuthenticated(errors: List<AuthenticateResponse>) =
            OciResponse(errors)
    }
}
