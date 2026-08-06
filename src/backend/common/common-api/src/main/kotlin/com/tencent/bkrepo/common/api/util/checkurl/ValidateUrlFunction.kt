package com.tencent.bkrepo.common.api.util.checkurl

import java.util.concurrent.ConcurrentHashMap

fun interface ValidateUrlFunction {
    fun validateUrl(rules: List<String>, domain: String): Boolean

    companion object {
        private val patternCache = ConcurrentHashMap<String, Regex>()

        val IS_DOMAIN = ValidateUrlFunction { rules, domain -> rules.contains(domain) }

        val IS_SUB_DOMAIN = ValidateUrlFunction { rules, subdomain ->
            rules.any { domain ->
                domain.isNotBlank() && (subdomain == domain || subdomain.endsWith(".$domain"))
            }
        }

        val IS_SUB_DOMAIN_REGEX = ValidateUrlFunction { domainRegexes, domain ->
            domainRegexes.any { regex ->
                val pattern = patternCache.computeIfAbsent(regex, ::Regex)
                pattern.containsMatchIn(domain)
            }
        }
    }
}
