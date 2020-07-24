package com.tencent.bkrepo.common.storage.innercos.http

enum class HttpProtocol {
    HTTP, HTTPS;

    fun getScheme(): String = this.name.toLowerCase() + "://"
}
