package com.tencent.bkrepo.media.service

import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Token服务
 * */
@Service
class TokenService(
    private val permissionManager: PermissionManager,
    private val temporaryTokenClient: ServiceTemporaryTokenClient,
) {

    fun createToken(tokenCreateRequest: TemporaryTokenCreateRequest): List<String> {
        return temporaryTokenClient.createToken(tokenCreateRequest).data?.map { it.token }.orEmpty()
    }

    /**
     * 校验[token]是否有效
     * @param token 待校验token
     * @param artifactInfo 访问构件信息
     * @param type 访问类型
     */
    fun validateToken(
        token: String,
        artifactInfo: ArtifactInfo,
        type: TokenType,
    ): TemporaryTokenInfo {
        val temporaryToken = checkToken(token)
        checkExpireTime(temporaryToken.expireDate)
        checkAccessType(temporaryToken.type, type)
        checkAccessResource(temporaryToken, artifactInfo)
        checkAuthorization(temporaryToken)
        checkAccessPermits(temporaryToken.permits)
        return temporaryToken
    }

    fun validateTokenOnTcp(
        token: String,
        artifactInfo: ArtifactInfo,
        type: TokenType,
    ): TemporaryTokenInfo {
        val temporaryToken = checkToken(token)
        checkExpireTime(temporaryToken.expireDate)
        checkAccessType(temporaryToken.type, type)
        checkAccessResource(temporaryToken, artifactInfo)
        checkAccessPermits(temporaryToken.permits)
        return temporaryToken
    }

    /**
     * 检查访问次数
     */
    private fun checkAccessPermits(permits: Int?) {
        permits?.let {
            if (it <= 0) {
                throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_EXPIRED)
            }
        }
    }

    /**
     * 检查授权用户和ip
     */
    private fun checkAuthorization(tokenInfo: TemporaryTokenInfo) {
        // 检查用户授权
        // 获取经过认证的uid
        val authenticatedUid = SecurityUtils.getUserId()
        // 使用认证uid校验授权
        if (tokenInfo.authorizedUserList.isNotEmpty() && authenticatedUid !in tokenInfo.authorizedUserList) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, authenticatedUid)
        }
        // 获取需要审计的uid
        val auditedUid = if (SecurityUtils.isAnonymous()) {
            HttpContextHolder.getRequest().getHeader(AUTH_HEADER_UID) ?: tokenInfo.createdBy
        } else {
            authenticatedUid
        }
        // 设置审计uid到session中
        HttpContextHolder.getRequestOrNull()?.setAttribute(USER_KEY, auditedUid)
        // 校验ip授权
        val clientIp = HttpContextHolder.getClientAddress()
        if (tokenInfo.authorizedIpList.isNotEmpty() && clientIp !in tokenInfo.authorizedIpList) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, clientIp)
        }
    }

    /**
     * 检查访问资源
     */
    private fun checkAccessResource(tokenInfo: TemporaryTokenInfo, artifactInfo: ArtifactInfo) {
        // 校验项目/仓库
        if (tokenInfo.projectId != artifactInfo.projectId || tokenInfo.repoName != artifactInfo.repoName) {
            throw ErrorCodeException(
                ArtifactMessageCode.TEMPORARY_TOKEN_INVALID,
                "${artifactInfo.projectId}/${artifactInfo.repoName}",
            )
        }
        // 校验路径
        if (!PathUtils.isSubPath(artifactInfo.getArtifactFullPath(), tokenInfo.fullPath)) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, artifactInfo.getArtifactFullPath())
        }
        // 校验创建人权限
        permissionManager.checkNodePermission(
            if (tokenInfo.type == TokenType.DOWNLOAD) PermissionAction.READ else PermissionAction.WRITE,
            artifactInfo.projectId,
            artifactInfo.repoName,
            artifactInfo.getArtifactFullPath(),
            userId = tokenInfo.createdBy,
        )
    }

    /**
     * 检查访问类型
     */
    private fun checkAccessType(grantedType: TokenType, accessType: TokenType) {
        if (grantedType != TokenType.ALL && grantedType != accessType) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, accessType)
        }
    }

    /**
     * 检查token是否过期
     */
    private fun checkExpireTime(expireDateString: String?) {
        expireDateString?.let {
            val expireDate = LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
            if (expireDate.isBefore(LocalDateTime.now())) {
                throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_EXPIRED)
            }
        }
    }

    /**
     * 检查token并返回token信息
     */
    private fun checkToken(token: String): TemporaryTokenInfo {
        if (token.isBlank()) {
            throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, token)
        }
        return temporaryTokenClient.getTokenInfo(token).data
            ?: throw ErrorCodeException(ArtifactMessageCode.TEMPORARY_TOKEN_INVALID, token)
    }

    /**
     * 减少[tokenInfo]访问次数
     * 如果[tokenInfo]访问次数 <= 1，则直接删除
     */
    fun decrementPermits(tokenInfo: TemporaryTokenInfo) {
        if (tokenInfo.permits == null) {
            return
        }
        if (tokenInfo.permits!! <= 1) {
            temporaryTokenClient.deleteToken(tokenInfo.token)
        } else {
            temporaryTokenClient.decrementPermits(tokenInfo.token)
        }
    }
}
