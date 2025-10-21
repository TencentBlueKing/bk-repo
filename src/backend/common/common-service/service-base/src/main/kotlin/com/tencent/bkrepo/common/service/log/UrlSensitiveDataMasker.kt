package com.tencent.bkrepo.common.service.log

import com.tencent.bkrepo.common.api.util.MaskPartStringUtil
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * URL敏感数据脱敏工具类
 * 用于对access日志中的敏感参数进行脱敏处理
 */
object UrlSensitiveDataMasker {

    /**
     * 需要脱敏的参数名列表（不区分大小写）
     */
    private val SENSITIVE_PARAMS = setOf(
        "token",
        "secretkey",
        "access_token",
        "accesstoken",
        "authorization",
        "password",
        "passwd",
        "pwd",
        "secret",
        "x-devops-bk-token",
        "x-devops-bk-ticket"
    )

    /**
     * 对URL进行脱敏处理
     * @param url 原始URL
     * @return 脱敏后的URL
     */
    fun maskSensitiveData(url: String?): String {
        if (url.isNullOrBlank()) {
            return url ?: ""
        }

        return try {
            val questionMarkIndex = url.indexOf('?')
            if (questionMarkIndex == -1) {
                // 没有查询参数，直接返回
                return url
            }

            val baseUrl = url.substring(0, questionMarkIndex)
            val queryString = url.substring(questionMarkIndex + 1)

            if (queryString.isBlank()) {
                return url
            }

            val maskedQueryString = maskQueryString(queryString)
            "$baseUrl?$maskedQueryString"
        } catch (e: Exception) {
            // 如果处理过程中出现异常，返回原始URL以避免日志记录失败
            url
        }
    }

    /**
     * 对查询字符串进行脱敏处理
     * @param queryString 查询字符串
     * @return 脱敏后的查询字符串
     */
    private fun maskQueryString(queryString: String): String {
        return queryString.split('&')
            .joinToString("&") { param ->
                maskParameter(param)
            }
    }

    /**
     * 对单个参数进行脱敏处理
     * @param param 参数字符串，格式为 key=value
     * @return 脱敏后的参数字符串
     */
    private fun maskParameter(param: String): String {
        val equalIndex = param.indexOf('=')
        if (equalIndex == -1) {
            // 没有等号，可能是无值参数，直接返回
            return param
        }

        val key = param.substring(0, equalIndex)
        val value = param.substring(equalIndex + 1)

        return if (isSensitiveParam(key)) {
            val maskValue = MaskPartStringUtil.maskPartString(value)
            "$key=$maskValue"
        } else {
            param
        }
    }

    /**
     * 判断参数是否为敏感参数
     * @param paramName 参数名
     * @return 是否为敏感参数
     */
    private fun isSensitiveParam(paramName: String): Boolean {
        val normalizedName = try {
            // 尝试URL解码参数名
            URLDecoder.decode(paramName, StandardCharsets.UTF_8.name()).lowercase()
        } catch (e: Exception) {
            // 解码失败，使用原始参数名
            paramName.lowercase()
        }

        return SENSITIVE_PARAMS.any { sensitiveParam ->
            normalizedName.contains(sensitiveParam)
        }
    }

    /**
     * 对请求行进行脱敏处理
     * 处理格式如: "GET /api/test?token=abc123 HTTP/1.1"
     * @param requestLine 请求行
     * @return 脱敏后的请求行
     */
    fun maskRequestLine(requestLine: String?): String {
        if (requestLine.isNullOrBlank()) {
            return requestLine ?: ""
        }

        return try {
            val parts = requestLine.split(' ')
            if (parts.size >= 2) {
                val method = parts[0]
                val url = parts[1]
                val protocol = if (parts.size >= 3) parts[2] else ""

                val maskedUrl = maskSensitiveData(url)
                if (protocol.isNotEmpty()) {
                    "$method $maskedUrl $protocol"
                } else {
                    "$method $maskedUrl"
                }
            } else {
                requestLine
            }
        } catch (e: Exception) {
            requestLine
        }
    }
}