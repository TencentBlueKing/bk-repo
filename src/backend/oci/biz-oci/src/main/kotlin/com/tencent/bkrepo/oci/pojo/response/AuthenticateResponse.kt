package com.tencent.bkrepo.oci.pojo.response

data class AuthenticateResponse(
    val code: Any? = null,
    val message: String? = null,
    val detail: String? = null
) {
    companion object {
        fun unAuthenticated(code: String, message: String?, detail: String?) =
            AuthenticateResponse(code = code, message = message, detail = detail)
    }
}
