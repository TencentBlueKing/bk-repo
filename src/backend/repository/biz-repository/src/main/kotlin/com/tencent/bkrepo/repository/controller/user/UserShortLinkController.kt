package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLink
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLinkListOption
import com.tencent.bkrepo.common.metadata.service.shortlink.ShortLinkService
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 短链接接口
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

    @Operation(summary = "查询短链接")
    @Principal(PrincipalType.ADMIN)
    @GetMapping("/info/{code}")
    fun get(@PathVariable code: String): Response<ShortLink> {
        val shortLink = shortLinkService.get(code)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, code)
        return ResponseBuilder.success(shortLink)
    }

    @Operation(summary = "删除短链接")
    @Principal(PrincipalType.ADMIN)
    @DeleteMapping("/{code}")
    fun delete(@PathVariable code: String): Response<Void> {
        shortLinkService.delete(code)
        return ResponseBuilder.success()
    }

    @Operation(summary = "按创建人分页查询短链接")
    @Principal(PrincipalType.ADMIN)
    @GetMapping("/list")
    fun list(
        @RequestParam createdBy: String,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_NUMBER") pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_SIZE") pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Response<Page<ShortLink>> {
        val option = ShortLinkListOption(createdBy = createdBy, pageNumber = pageNumber, pageSize = pageSize)
        return ResponseBuilder.success(shortLinkService.listByCreator(option))
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
