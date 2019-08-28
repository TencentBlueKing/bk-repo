package com.tencent.repository.common.service.util

import javax.servlet.http.HttpServletRequest

object CookieUtils {
    fun getCookieValue(request: HttpServletRequest, name: String): String? {

        // cookie数组
        val cookies = request.cookies
        if (null != cookies) {
            for (cookie in cookies) {
                if (cookie.name == name) {
                    return cookie.value
                }
            }
        }

        var value: String? = null
        // Cookie属性中没有获取到，那么从Headers里面获取
        var cookieStr: String? = request.getHeader("Cookie")
        if (cookieStr != null) {
            // 去掉所有空白字符，不限于空格
            cookieStr = cookieStr.replace("\\s*".toRegex(), "")
            val cookieArr = cookieStr.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            for (cookieItem in cookieArr) {
                val cookieItemArr = cookieItem.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (cookieItemArr[0] == name) {
                    value = cookieItemArr[1]
                    break
                }
            }
        }
        return value
    }
}
