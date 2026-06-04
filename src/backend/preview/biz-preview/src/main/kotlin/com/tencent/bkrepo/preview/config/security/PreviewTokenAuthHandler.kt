/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 */

package com.tencent.bkrepo.preview.config.security

import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.TEMPORARY_TOKEN_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.http.core.HttpAuthHandler
import com.tencent.bkrepo.common.security.http.credentials.AnonymousCredentials
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import com.tencent.bkrepo.common.security.http.temporary.TemporaryTokenAuthCredentials
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * preview 微服务专用的临时 token 鉴权 handler。
 *
 * 通过在 preview 模块的 `HttpAuthSecurityCustomizer` 中：
 *   1. 把默认 `httpAuthSecurity.temporaryTokenEnabled = false`（阻止 common 默认 handler 注册）；
 *   2. 调 `httpAuthSecurity.addHttpAuthHandler(PreviewTokenAuthHandler(...))` 注入本 handler。
 * 这样 preview 完全替换了默认的 [com.tencent.bkrepo.common.security.http.temporary.TemporaryTokenAuthHandler]，
 * 而 common-security 一行代码不动，影响范围严格闭环在 preview 微服务。
 *
 * 承载 4 项核心能力：
 *  1. 双传递方式（Header `Authorization: Temporary` + query `?token=`）；
 *  2. 范围校验：projectId/repoName/fullPath/expireDate/permits/IP；
 *  3. 匿名/定向双模式：根据 `tokenInfo.authorizedUserList` 是否为空走不同的身份绑定逻辑；
 *  4. 紧急关停：`preview.temporary-token.enabled = false` 时短路回退到登录态链路。
 *
 * 仅 `type=PREVIEW` 的 token 才会被本 handler 接受；其它类型的 token 直接拒绝认证。
 */
class PreviewTokenAuthHandler(
    private val authenticationManager: AuthenticationManager,
    private val temporaryTokenClient: ServiceTemporaryTokenClient,
    private val config: PreviewTokenAuthConfig = PreviewTokenAuthConfig(),
) : HttpAuthHandler {

    /**
     * 从 HTTP 请求中提取临时 token 凭据。
     *
     * 支持两种传递方式：
     *  - Header：`Authorization: Temporary <token>`（保留原行为）
     *  - Query： `?token=<token>` （iframe / PDF.js worker 等无法注入 Header 的兜底直链）
     *
     * 当二者同时存在且不一致时：以 Header 为准，并以 WARN 级别输出冲突告警。
     * 当二者都不存在时返回 [AnonymousCredentials]，由后续 handler 处理（不影响登录态链路）。
     *
     * 紧急关停：`preview.temporary-token.enabled = false` 时直接返回匿名凭据。
     */
    override fun extractAuthCredentials(request: HttpServletRequest): HttpAuthCredentials {
        if (!config.enabled) {
            return AnonymousCredentials()
        }
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION).orEmpty().trim()
        val headerToken: String? = if (authorizationHeader.startsWith(TEMPORARY_TOKEN_AUTH_PREFIX)) {
            authorizationHeader.removePrefix(TEMPORARY_TOKEN_AUTH_PREFIX).trim().takeIf { it.isNotBlank() }
        } else {
            null
        }
        val queryToken: String? = request.getParameter(QUERY_PARAM_TOKEN)?.trim()?.takeIf { it.isNotBlank() }

        if (headerToken == null && queryToken == null) {
            return AnonymousCredentials()
        }

        if (headerToken != null && queryToken != null && headerToken != queryToken) {
            logger.warn(
                "TemporaryToken header/query mismatch on ${request.method} ${request.requestURI}, " +
                    "use header value (header=${maskToken(headerToken)}, query=${maskToken(queryToken)})"
            )
        }

        val token = headerToken ?: queryToken!!
        return TemporaryTokenAuthCredentials(token)
    }

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        require(authCredentials is TemporaryTokenAuthCredentials)
        val token = authCredentials.token
        val tokenInfo = authenticationManager.getTokenInfo(token)
            ?: throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, maskToken(token))

        // preview 链路只接受 PREVIEW 类型的 token；其它类型一律拒绝，避免误识别为身份凭据
        if (tokenInfo.type != TokenType.PREVIEW) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, maskToken(token))
        }

        validateTokenScope(tokenInfo, request)
        val effectiveUid = bindUserAndAudit(tokenInfo, request)
        checkUserId(effectiveUid)
        request.setAttribute(USER_KEY, effectiveUid)
        request.setAttribute(REQ_ATTR_TEMP_TOKEN_INFO, tokenInfo)
        return effectiveUid
    }

    /**
     * 范围校验：逐项校验 projectId/repoName/fullPath/expireDate/permits/IP。
     * 校验项语义对齐 generic `TemporaryAccessService.validateToken`，保持一致性。
     */
    private fun validateTokenScope(tokenInfo: TemporaryTokenInfo, request: HttpServletRequest) {
        // 1. 过期时间校验
        tokenInfo.expireDate?.let {
            val expireDate = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
            if (expireDate.isBefore(LocalDateTime.now())) {
                throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_EXPIRED)
            }
        }

        // 2. permits 剩余次数校验
        tokenInfo.permits?.let {
            if (it <= 0) {
                throw ErrorCodeException(PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_PERMITS_EXHAUSTED)
            }
        }

        // 3. URL 范围校验（fullPath）：在 URI 中按 token 自身的 projectId/repoName 定位资源段，
        //    成功定位即可推导出 fullPath，必然落在 token 的 project/repo 范围内
        val requestFullPath = locateRequestFullPath(request, tokenInfo)
        if (requestFullPath != null) {
            if (!isSubPath(requestFullPath, tokenInfo.fullPath)) {
                logger.warn(
                    "TemporaryToken scope mismatch (fullPath): " +
                        "uri=${request.requestURI}, token=${maskToken(tokenInfo.token)}, " +
                        "tokenPath=${tokenInfo.fullPath}, requestPath=$requestFullPath"
                )
                throw ErrorCodeException(
                    PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_OUT_OF_SCOPE,
                    requestFullPath
                )
            }
        } else {
            logger.info(
                "TemporaryToken: skip path-scope check, no project/repo segment in uri=${request.requestURI}"
            )
        }

        // 4. IP 白名单校验
        if (tokenInfo.authorizedIpList.isNotEmpty()) {
            val clientIp = resolveClientIp(request)
            if (clientIp !in tokenInfo.authorizedIpList) {
                throw ErrorCodeException(
                    PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_OUT_OF_SCOPE,
                    "ip:$clientIp"
                )
            }
        }

        // 5. permits 原子扣减
        tokenInfo.permits?.let {
            if (it <= 1) {
                temporaryTokenClient.deleteToken(tokenInfo.token)
            } else {
                temporaryTokenClient.decrementPermits(tokenInfo.token)
            }
        }
    }

    /**
     * 匿名/定向双模式身份绑定：
     *  - authorizedUserList 为空 → 匿名分享：写入 SecurityContext 的 uid 取 `tokenInfo.createdBy`；
     *  - 非空 → 定向分享：要求请求带 `X-BKREPO-UID`；未登录抛 401，登录但 uid 不在 list 抛 403。
     */
    private fun bindUserAndAudit(
        tokenInfo: TemporaryTokenInfo,
        request: HttpServletRequest,
    ): String {
        val gatewayUid = request.getHeader(AUTH_HEADER_UID)?.trim()?.takeIf { it.isNotBlank() }
        val effectiveUid: String = if (tokenInfo.authorizedUserList.isEmpty()) {
            // 匿名分享：使用 token 创建者身份做下游审计与权限兜底
            tokenInfo.createdBy
        } else {
            // 定向分享：必须先通过网关完成登录
            if (gatewayUid == null || gatewayUid == ANONYMOUS_USER) {
                throw AuthenticationException(PreviewMessageCode.PREVIEW_LOGIN_REQUIRED.name)
            }
            if (gatewayUid !in tokenInfo.authorizedUserList) {
                throw PermissionException(PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_USER_FORBIDDEN.name)
            }
            gatewayUid
        }
        if (logger.isInfoEnabled) {
            logger.info(
                "TemporaryToken auth ok: " +
                    "tokenId=${maskToken(tokenInfo.token)}, " +
                    "createdBy=${tokenInfo.createdBy}, visitor=$effectiveUid, " +
                    "projectId=${tokenInfo.projectId}, repoName=${tokenInfo.repoName}, " +
                    "fullPath=${tokenInfo.fullPath}, " +
                    "uri=${request.requestURI}, ip=${resolveClientIp(request)}, " +
                    "ua=${request.getHeader(HttpHeaders.USER_AGENT)?.take(120) ?: "-"}"
            )
        }
        return effectiveUid
    }

    /**
     * 在 URL path segments 中定位 `tokenInfo.projectId / tokenInfo.repoName` 连续相邻的位置，
     * 用其后续段拼成 fullPath 返回。若未匹配到则返回 null。
     */
    private fun locateRequestFullPath(request: HttpServletRequest, tokenInfo: TemporaryTokenInfo): String? {
        val segments = request.requestURI.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null
        for (i in 0..segments.size - 2) {
            if (segments[i] == tokenInfo.projectId && segments[i + 1] == tokenInfo.repoName) {
                return if (i + 2 < segments.size) {
                    "/" + segments.drop(i + 2).joinToString("/")
                } else {
                    "/"
                }
            }
        }
        return null
    }

    /**
     * 与 PathUtils.isSubPath 等价的本地实现，避免引入额外依赖。
     */
    private fun isSubPath(path: String, parent: String): Boolean {
        val formatParent = if (parent.startsWith('/')) parent else "/$parent"
        return path.startsWith(formatParent)
    }

    /**
     * 解析请求来源 IP：优先取 `X-Forwarded-For` 首段，其次取 `X-Real-IP`，最后回退到 RemoteAddr。
     */
    private fun resolveClientIp(request: HttpServletRequest): String {
        val xff = request.getHeader("X-Forwarded-For")?.trim()
        if (!xff.isNullOrBlank()) {
            return xff.split(',').first().trim()
        }
        val xRealIp = request.getHeader("X-Real-IP")?.trim()
        if (!xRealIp.isNullOrBlank()) {
            return xRealIp
        }
        return request.remoteAddr ?: ""
    }

    private fun checkUserId(userId: String) {
        if (authenticationManager.findUserAccount(userId) == null) {
            authenticationManager.createUserAccount(userId)
            logger.info("Create user [$userId] success.")
        }
    }

    /**
     * 对 token 做脱敏，仅保留首尾 4 字符；用于日志输出，避免泄露完整 token。
     */
    private fun maskToken(token: String): String {
        return if (token.length <= 8) "***" else "${token.take(4)}***${token.takeLast(4)}"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PreviewTokenAuthHandler::class.java)

        /**
         * URL query 参数中传递临时 token 的参数名。
         */
        const val QUERY_PARAM_TOKEN = "token"

        /**
         * 鉴权通过后写入到 [HttpServletRequest] 上的 token 详细信息属性 key，
         * 供 [PreviewArtifactPermissionCheckHandler] 与下游 controller 读取。
         */
        const val REQ_ATTR_TEMP_TOKEN_INFO = "bkrepo.preview.temporary.token.info"
    }
}
