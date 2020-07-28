package com.tencent.bkrepo.composer.util

import javax.servlet.http.HttpServletRequest

object HttpUtil {
    private const val requestAddrFormat = "%s://%s:%d"

    fun HttpServletRequest.requestAddr(): String {
        return this.let {
            String.format(requestAddrFormat, it.protocol, it.remoteHost, it.remotePort)
        }
    }
}
