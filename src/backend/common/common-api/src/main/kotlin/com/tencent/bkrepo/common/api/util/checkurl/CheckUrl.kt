package com.tencent.bkrepo.common.api.util.checkurl

import java.net.MalformedURLException
import java.net.URL

object CheckUrl {

    private const val DOMAIN_REGEX =
        "^(?=^.{3,255}$)[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+$"

    private val domainPattern = Regex(DOMAIN_REGEX)

    private val modeValues = setOf("equal", "subhost", "regex")

    private val modeMatchMethodMap = mapOf(
        "equal" to ValidateUrlFunction.IS_DOMAIN,
        "subhost" to ValidateUrlFunction.IS_SUB_DOMAIN,
        "regex" to ValidateUrlFunction.IS_SUB_DOMAIN_REGEX,
    )

    @Throws(IllegalArgumentException::class, MalformedURLException::class)
    fun checkUrl(url: String, config: UrlCheckConfig) {
        if (url.isBlank()) {
            throw IllegalArgumentException("Url is null.")
        }
        if (isConfigIllegal(config)) {
            throw IllegalArgumentException("Config is illegal.")
        }
        if (url.startsWith('/')) {
            return
        }
        val urlParsed = try {
            URL(url)
        } catch (_: MalformedURLException) {
            throw MalformedURLException("URL can not be parsed.")
        }
        if (!isHostnameValid(urlParsed.host)) {
            throw MalformedURLException("Hostname is not valid")
        }
        if (urlParsed.protocol !in config.schemes) {
            throw MalformedURLException("Scheme is not valid.")
        }
        val validator = modeMatchMethodMap[config.mode]
            ?: throw IllegalArgumentException("Mode is not valid.")
        if (!validator.validateUrl(config.rules, urlParsed.host)) {
            throw MalformedURLException("URL does not meet the conditions.")
        }
    }

    private fun isConfigIllegal(config: UrlCheckConfig): Boolean {
        return config.schemes.isEmpty() || config.rules.isEmpty() ||
            config.mode.isBlank() || config.mode !in modeValues
    }

    private fun isHostnameValid(hostname: String): Boolean = domainPattern.containsMatchIn(hostname)
}
