package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.metadata.model.TShortLink
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLink
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLinkCreateRequest
import com.tencent.bkrepo.common.metadata.properties.ShortLinkProperties
import java.net.URI
import java.time.LocalDateTime

/**
 * 短链接公共逻辑
 */
object ShortLinkHelper {

    const val CODE_LENGTH = 8
    const val MAX_CODE_RETRY = 5
    private const val PATH_PREFIX = "/"

    fun validateTarget(target: String, allowedHosts: List<String>) {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "target")
        }
        if (trimmed.startsWith(PATH_PREFIX)) {
            if (trimmed.startsWith("//")) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, trimmed)
            }
            return
        }
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, trimmed)
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, trimmed)
        }
        val host = uri.host?.lowercase()
            ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, trimmed)
        if (allowedHosts.isEmpty() || !hostMatches(host, allowedHosts)) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, host)
        }
    }

    fun resolveExpiredDate(request: ShortLinkCreateRequest, properties: ShortLinkProperties): LocalDateTime {
        val now = LocalDateTime.now()
        val maxExpired = now.plusDays(properties.maxTtlDays)
        val expiredDate = request.expiredDate ?: now.plusDays(properties.defaultTtlDays)
        if (!expiredDate.isAfter(now) || expiredDate.isAfter(maxExpired)) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "expiredDate")
        }
        return expiredDate
    }

    fun generateCode(): String = StringPool.randomString(CODE_LENGTH)

    fun buildShortUrl(code: String, publicHost: String): String {
        val path = "/t/$code"
        val host = publicHost.trim()
        if (host.isEmpty()) {
            return path
        }
        return when {
            host.startsWith(StringPool.HTTP) || host.startsWith(StringPool.HTTPS) ->
                host.trimEnd('/') + path
            else -> StringPool.HTTPS + host.trimEnd('/') + path
        }
    }

    fun resolveAbsoluteUrl(target: String, scheme: String, host: String): String {
        val trimmed = target.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        val normalizedScheme = scheme.ifBlank { "https" }
        val normalizedHost = host.trim().trimEnd('/')
        if (normalizedHost.isEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "host")
        }
        val path = if (trimmed.startsWith(PATH_PREFIX)) trimmed else "$PATH_PREFIX$trimmed"
        return "$normalizedScheme://$normalizedHost$path"
    }

    fun ensureNotExpired(record: TShortLink) {
        if (record.expiredDate.isBefore(LocalDateTime.now())) {
            throw ErrorCodeException(
                status = HttpStatus.GONE,
                messageCode = CommonMessageCode.RESOURCE_EXPIRED,
                params = arrayOf(record.code),
            )
        }
    }

    fun TShortLink.toShortLink(publicHost: String): ShortLink {
        return ShortLink(
            code = code,
            target = target,
            shortUrl = buildShortUrl(code, publicHost),
            expiredDate = expiredDate,
            createdBy = createdBy,
            createdDate = createdDate,
        )
    }

    fun buildEntity(request: ShortLinkCreateRequest, code: String, expiredDate: LocalDateTime): TShortLink {
        val now = LocalDateTime.now()
        return TShortLink(
            code = code,
            target = request.target.trim(),
            expiredDate = expiredDate,
            createdBy = request.createdBy,
            createdDate = now,
            lastModifiedBy = request.createdBy,
            lastModifiedDate = now,
        )
    }

    private fun hostMatches(host: String, patterns: List<String>): Boolean {
        return patterns.any { raw ->
            val pattern = raw.trim().lowercase()
            when {
                pattern.isEmpty() -> false
                pattern.startsWith(".") -> host == pattern.substring(1) || host.endsWith(pattern)
                else -> host == pattern
            }
        }
    }
}
