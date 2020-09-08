package com.tencent.bkrepo.common.artifact.util.http

import com.tencent.bkrepo.common.api.constant.CharPool.QUESTION
import com.tencent.bkrepo.common.api.constant.CharPool.SLASH
import com.tencent.bkrepo.common.api.constant.StringPool.HTTP
import com.tencent.bkrepo.common.api.constant.StringPool.HTTPS

/**
 * Http URL 格式化工具类
 */
object UrlFormatter {

    /**
     * 格式化url
     */
    fun format(host: String, uri: String? = null, query: String? = null): String {
        val builder = StringBuilder()
        builder.append(formatHost(host))
        if (!uri.isNullOrBlank()) {
            builder.append(uri.trim(SLASH))
        }
        if (!query.isNullOrBlank()) {
            builder.append(QUESTION).append(query.trim(SLASH))
        }
        return builder.toString()
    }

    /**
     * 格式化[host]
     * http://xxx.com/// -> http://xxx.com/
     */
    fun formatHost(host: String): String {
        return host.trim().trimEnd(SLASH).plus(SLASH)
    }

    /**
     * 格式化url
     */
    @Throws(IllegalArgumentException::class)
    fun formatUrl(value: String): String {
        var url = value.trim()
        if (url.isBlank()) {
            throw IllegalArgumentException("Url should not be blank")
        }
        if (!url.startsWith(HTTP) || !url.startsWith(HTTPS)) {
            url = HTTP + url
        }
        return url
    }
}
