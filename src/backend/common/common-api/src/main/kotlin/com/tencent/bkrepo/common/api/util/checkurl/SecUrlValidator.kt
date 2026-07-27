package com.tencent.bkrepo.common.api.util.checkurl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.UrlFormatter
import java.net.MalformedURLException

object SecUrlValidator {

    fun validate(rawUrl: String, properties: UrlCheckProperties): String {
        if ('\r' in rawUrl || '\n' in rawUrl) {
            throw IllegalArgumentException("invalid url")
        }
        val formatted = try {
            UrlFormatter.formatUrl(rawUrl.trim())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("invalid url")
        }
        CheckUrl.checkUrl(formatted, properties.toConfig())
        return formatted
    }

    fun validateOrThrow(rawUrl: String, properties: UrlCheckProperties, fieldName: String): String {
        return try {
            validate(rawUrl, properties)
        } catch (_: IllegalArgumentException) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, fieldName)
        } catch (_: MalformedURLException) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, fieldName)
        }
    }

    fun isValid(rawUrl: String, properties: UrlCheckProperties): Boolean {
        return try {
            validate(rawUrl, properties)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun UrlCheckProperties.toConfig(): UrlCheckConfig {
        return if (rules.isEmpty()) {
            UrlCheckConfig(
                schemes = schemes.toMutableList(),
                mode = "regex",
                rules = mutableListOf(".*"),
            )
        } else {
            UrlCheckConfig(
                schemes = schemes.toMutableList(),
                mode = mode,
                rules = rules.toMutableList(),
            )
        }
    }
}
