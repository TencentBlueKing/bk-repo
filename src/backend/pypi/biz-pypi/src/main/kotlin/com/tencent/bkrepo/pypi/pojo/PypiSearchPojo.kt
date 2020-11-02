package com.tencent.bkrepo.pypi.pojo

data class PypiSearchPojo(
    val action: String,
    val name: String?,
    val summary: String?,
    val operation: String
)
