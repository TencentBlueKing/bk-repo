package com.tencent.bkrepo.maven.pojo

enum class ResultStatusCode(
        val errorCode: Int,
        val errorMessage: String
) {
    OK(0, "OK"),
    PERMISSION_DENIED(500, "PERMISSION_DENIED"),
    SYSTEM_ERR(30001, "System root"),
    NOT_FOUND(404, "NOT_FOUND");
}