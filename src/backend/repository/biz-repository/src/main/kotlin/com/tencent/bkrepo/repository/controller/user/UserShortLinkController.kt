package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.metadata.service.shortlink.ShortLinkService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 短链接跳转接口（匿名可访问）
 */
@Tag(name = "短链接接口")
@RestController
@RequestMapping("/api/shortlink")
class UserShortLinkController(
    private val shortLinkService: ShortLinkService,
) {

    @Operation(summary = "短链接跳转")
    @GetMapping("/{code}")
    fun redirect(
        @PathVariable code: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val scheme = resolveScheme(request)
        val host = resolveHost(request)
        val target = shortLinkService.resolve(code, scheme, host)
        response.sendRedirect(target)
    }

    private fun resolveScheme(request: HttpServletRequest): String {
        val forwarded = request.getHeader(HEADER_X_FORWARDED_PROTO)
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(',').first().trim()
        }
        return request.scheme
    }

    private fun resolveHost(request: HttpServletRequest): String {
        val forwarded = request.getHeader(HEADER_X_FORWARDED_HOST)
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(',').first().trim()
        }
        val hostHeader = request.getHeader(HEADER_HOST)
        if (!hostHeader.isNullOrBlank()) {
            return hostHeader
        }
        val serverName = request.serverName
        val port = request.serverPort
        return if (port == 80 || port == 443) {
            serverName
        } else {
            "$serverName:$port"
        }
    }

    companion object {
        private const val HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto"
        private const val HEADER_X_FORWARDED_HOST = "X-Forwarded-Host"
        private const val HEADER_HOST = "Host"
    }
}
