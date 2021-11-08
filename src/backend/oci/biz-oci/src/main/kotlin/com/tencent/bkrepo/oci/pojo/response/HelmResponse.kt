package com.tencent.bkrepo.oci.pojo.response

data class HelmResponse<T>(
    val errors: List<T>
) {
    companion object {
        fun unAuthenticated(errors: List<AuthenticateResponse>) =
            HelmResponse(errors)
    }
}
