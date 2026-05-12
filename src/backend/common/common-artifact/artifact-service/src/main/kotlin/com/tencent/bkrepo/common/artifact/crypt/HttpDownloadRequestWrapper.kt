package com.tencent.bkrepo.common.artifact.crypt

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import org.springframework.http.HttpHeaders

class HttpDownloadRequestWrapper(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

    override fun getHeader(name: String): String? {
        // 不支持部分解密，所以这里需要下载全部数据
        if (name == HttpHeaders.RANGE) {
            return null
        }
        return super.getHeader(name)
    }
}