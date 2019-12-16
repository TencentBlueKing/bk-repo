package com.tencent.bkrepo.maven.pojo

data class ResultMsg(
        val errorCode: Int,
        val errorMessage: String,
        val data: Any?
) {

}