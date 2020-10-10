package com.tencent.bkrepo.dockeradapter.client;

data class PaasResponse<T>(
    val code: Int,
    val data: T?,
    val message: String?,
    val result: Boolean?
)