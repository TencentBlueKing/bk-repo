package com.tencent.bkrepo.preview.config.security

import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.token.OrgScope
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.auth.pojo.token.matchesAuthorizedOrgIds
import com.tencent.bkrepo.auth.pojo.token.normalizeOrgIds
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.TEMPORARY_TOKEN_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * preview 微服务专用的临时 token 鉴权服务（阶段二）。
 *
 * 在 [PreviewTokenPostAuthInterceptor] 中于阶段一 [com.tencent.bkrepo.common.security.http.core.HttpAuthInterceptor]
 * 之后调用：只要请求携带 preview token，即强制执行范围校验与身份绑定，不再注册为链内 [com.tencent.bkrepo.common.security.http.core.HttpAuthHandler]。
 *
 * 承载核心能力：
 *  1. 双传递方式（Header `Authorization: Temporary` + query `?token=`）；
 *  2. 范围校验：projectId/repoName/fullPath/expireDate/permits/IP；
 *  3. 身份授权：匿名分享（用户与组织皆空）绑定 createdBy；定向为用户列表或组织 OR；
 *  4. 紧急关停：`preview.temporary-token.enabled = false` 时短路跳过。
 *
 * 仅 `type=PREVIEW` 的 token 才会被接受；其它类型的 token 直接拒绝认证。
 */
class PreviewTokenAuthService(
    private val authenticationManager: AuthenticationManager,
    private val temporaryTokenClient: ServiceTemporaryTokenClient,
    private val serviceUserClient: ServiceUserClient,
    private val config: PreviewTokenAuthConfig = PreviewTokenAuthConfig(),
) {

    /**
     * 若请求携带 preview token 则执行完整鉴权；无 token 或紧急关停时直接返回。
     */
    fun authenticateIfPresent(request: HttpServletRequest) {
        if (!config.enabled) {
            return
        }
        val token = resolveToken(request) ?: return

        ensureUriAllowedForPreviewToken(request)

        val tokenInfo = authenticationManager.getTokenInfo(token)
            ?: throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, maskToken(token))

        if (tokenInfo.type != TokenType.PREVIEW) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, maskToken(token))
        }

        validateTokenScope(tokenInfo, request)
        val effectiveUid = bindUserAndAudit(tokenInfo, request)
        checkUserId(effectiveUid)
        request.setAttribute(USER_KEY, effectiveUid)
        request.setAttribute(REQ_ATTR_TEMP_TOKEN_INFO, tokenInfo)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION).orEmpty().trim()
        val headerToken: String? = if (authorizationHeader.startsWith(TEMPORARY_TOKEN_AUTH_PREFIX)) {
            authorizationHeader.removePrefix(TEMPORARY_TOKEN_AUTH_PREFIX).trim().takeIf { it.isNotBlank() }
        } else {
            null
        }
        val queryToken: String? = request.getParameter(QUERY_PARAM_TOKEN)?.trim()?.takeIf { it.isNotBlank() }

        if (headerToken == null && queryToken == null) {
            return null
        }

        if (headerToken != null && queryToken != null && headerToken != queryToken) {
            logger.warn(
                "TemporaryToken header/query mismatch on ${request.method} ${request.requestURI}, " +
                    "use header value (header=${maskToken(headerToken)}, query=${maskToken(queryToken)})"
            )
        }

        return headerToken ?: queryToken
    }

    private fun validateTokenScope(tokenInfo: TemporaryTokenInfo, request: HttpServletRequest) {
        tokenInfo.expireDate?.let {
            val expireDate = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
            if (expireDate.isBefore(LocalDateTime.now())) {
                throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_EXPIRED)
            }
        }

        tokenInfo.permits?.let {
            if (it <= 0) {
                throw ErrorCodeException(PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_PERMITS_EXHAUSTED)
            }
        }

        val requestFullPath = locateRequestFullPath(request, tokenInfo)
        if (requestFullPath == null) {
            logger.warn(
                "TemporaryToken scope reject: cannot locate project/repo segment, " +
                    "uri=${request.requestURI}, token=${maskToken(tokenInfo.token)}, " +
                    "projectId=${tokenInfo.projectId}, repoName=${tokenInfo.repoName}"
            )
            throw ErrorCodeException(
                PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_OUT_OF_SCOPE,
                request.requestURI
            )
        }
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

        if (tokenInfo.authorizedIpList.isNotEmpty()) {
            val clientIp = resolveClientIp(request)
            if (clientIp !in tokenInfo.authorizedIpList) {
                throw ErrorCodeException(
                    PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_OUT_OF_SCOPE,
                    "ip:$clientIp"
                )
            }
        }

        tokenInfo.permits?.let {
            if (it <= 1) {
                temporaryTokenClient.deleteToken(tokenInfo.token)
            } else {
                temporaryTokenClient.decrementPermits(tokenInfo.token)
            }
        }
    }

    /**
     *  - authorizedUserList 与 authorizedOrgList 皆空 → 匿名分享：`USER_KEY` 取 `tokenInfo.createdBy`；
     *  - 否则为定向分享：须已登录；uid 命中用户列表，或组织 ID 命中用户任一 scopeValue。
     */
    private fun bindUserAndAudit(
        tokenInfo: TemporaryTokenInfo,
        request: HttpServletRequest,
    ): String {
        val phaseOneUid = (request.getAttribute(USER_KEY) as? String)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: ANONYMOUS_USER
        val effectiveUid: String = if (tokenInfo.authorizedUserList.isEmpty() &&
            tokenInfo.authorizedOrgList.isEmpty()
        ) {
            tokenInfo.createdBy
        } else {
            if (phaseOneUid == ANONYMOUS_USER) {
                throw AuthenticationException(PreviewMessageCode.PREVIEW_LOGIN_REQUIRED.name)
            }
            if (phaseOneUid !in tokenInfo.authorizedUserList &&
                !matchesAuthorizedOrg(phaseOneUid, tokenInfo.authorizedOrgList)
            ) {
                throw PermissionException(PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_USER_FORBIDDEN.name)
            }
            phaseOneUid
        }
        if (logger.isInfoEnabled) {
            logger.info(
                "TemporaryToken auth ok: " +
                    "tokenId=${maskToken(tokenInfo.token)}, " +
                    "createdBy=[${tokenInfo.createdBy}], visitor=[$effectiveUid], " +
                    "projectId=[${tokenInfo.projectId}], repoName=[${tokenInfo.repoName}], " +
                    "fullPath=[${tokenInfo.fullPath}], " +
                    "uri=[${request.requestURI}], ip=[${resolveClientIp(request)}], " +
                    "ua=[${request.getHeader(HttpHeaders.USER_AGENT)?.take(120) ?: "-"}]"
            )
        }
        return effectiveUid
    }

    private fun matchesAuthorizedOrg(userId: String, authorizedOrgIds: Set<String>): Boolean {
        if (userId == ANONYMOUS_USER || authorizedOrgIds.normalizeOrgIds().isEmpty()) {
            return false
        }
        return loadUserOrgScopesOrThrow(userId).matchesAuthorizedOrgIds(authorizedOrgIds)
    }

    private fun loadUserOrgScopesOrThrow(userId: String): List<OrgScope> {
        return try {
            serviceUserClient.userDeptById(userId).data?.scopes
                ?: throw PermissionException(PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_USER_FORBIDDEN.name)
        } catch (ex: PermissionException) {
            throw ex
        } catch (ex: Exception) {
            logger.warn("Failed to resolve user org for temporary token auth, user=[$userId]", ex)
            throw PermissionException(PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_USER_FORBIDDEN.name)
        }
    }

    private fun locateRequestFullPath(request: HttpServletRequest, tokenInfo: TemporaryTokenInfo): String? {
        val segments = request.requestURI.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null
        for (i in 0..segments.size - 2) {
            if (segments[i] == tokenInfo.projectId && segments[i + 1] == tokenInfo.repoName) {
                val encodedPath = if (i + 2 < segments.size) {
                    "/" + segments.drop(i + 2).joinToString("/")
                } else {
                    "/"
                }
                // requestURI 保留 URL encode，token.fullPath 为解码路径，比较前需统一解码
                return UriUtils.decode(encodedPath, StandardCharsets.UTF_8)
            }
        }
        return null
    }

    private fun isSubPath(path: String, parent: String): Boolean {
        val formatParent = if (parent.startsWith('/')) parent else "/$parent"
        if (formatParent.endsWith('/')) {
            return path.startsWith(formatParent)
        }
        return path == formatParent || path.startsWith("$formatParent/")
    }

    private fun ensureUriAllowedForPreviewToken(request: HttpServletRequest) {
        val uri = request.requestURI ?: ""
        var allowed = false
        for (prefix in ALLOWED_URI_PREFIXES) {
            if (uri.contains(prefix)) {
                allowed = true
                break
            }
        }
        if (!allowed) {
            logger.warn(
                "TemporaryToken reject: uri not allowed for PREVIEW token, uri=$uri, method=${request.method}"
            )
            throw ErrorCodeException(
                PreviewMessageCode.PREVIEW_TEMPORARY_TOKEN_OUT_OF_SCOPE,
                uri
            )
        }
    }

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

    private fun maskToken(token: String): String {
        return if (token.length <= 8) "***" else "${token.take(4)}***${token.takeLast(4)}"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PreviewTokenAuthService::class.java)

        const val QUERY_PARAM_TOKEN = "token"

        const val REQ_ATTR_TEMP_TOKEN_INFO = "bkrepo.preview.temporary.token.info"

        private val ALLOWED_URI_PREFIXES = listOf(
            "/api/file/onlinePreview/",
            "/api/file/getPreviewInfo/",
        )
    }
}
