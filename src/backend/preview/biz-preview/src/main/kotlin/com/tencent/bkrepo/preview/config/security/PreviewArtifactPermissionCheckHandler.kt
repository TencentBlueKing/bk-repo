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

import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.common.artifact.permission.ArtifactPermissionCheckHandler
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.permission.PermissionCheckHandler
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.slf4j.LoggerFactory

/**
 * preview 微服务专用的 [PermissionCheckHandler]。
 *
 * 由于 [ArtifactPermissionCheckHandler] 是 final 类无法继承，这里采用 Kotlin **接口委托**：
 * 默认所有方法转发给被委托的 [delegate]，仅在 [onPermissionCheck] 入口加一层判定 ——
 * 若当前请求已通过 [PreviewTokenAuthService] 完成临时 token 鉴权
 * （request attribute 中存在 [PreviewTokenAuthService.REQ_ATTR_TEMP_TOKEN_INFO]），
 * 则按 token 是否匿名分享走两条不同的安全路径：
 *
 *  1. **匿名分享**（`authorizedUserList` 与 `authorizedOrgList` 皆空）：
 *     用 `tokenInfo.createdBy` 再走一遍默认 ACL 校验链。
 *
 *  2. **定向分享**（授权用户或组织范围非空）：
 *     直接放行 ACL；身份已在 token 阶段按用户列表或组织 OR 校验。
 *
 * 其他场景全部回退到被委托对象的默认行为，因此对 preview 服务的其他端点零侵入。
 */
class PreviewArtifactPermissionCheckHandler(
    private val delegate: ArtifactPermissionCheckHandler,
) : PermissionCheckHandler by delegate {

    override fun onPermissionCheck(userId: String, permission: Permission) {
        val tokenInfo = currentTemporaryTokenInfo()
        if (tokenInfo != null) {
            if (tokenInfo.authorizedUserList.isEmpty() && tokenInfo.authorizedOrgList.isEmpty()) {
                if (logger.isDebugEnabled) {
                    logger.debug(
                        "PreviewArtifactPermissionCheck(anonymous-share): delegate ACL with createdBy=" +
                            "${tokenInfo.createdBy}, visitor=$userId, " +
                            "permission=${permission.type}/${permission.action}"
                    )
                }
                delegate.onPermissionCheck(tokenInfo.createdBy, permission)
                return
            }
            if (logger.isInfoEnabled) {
                logger.info(
                    "PreviewArtifactPermissionCheck(directed-share) bypass ACL: " +
                        "createdBy=[${tokenInfo.createdBy}], visitor=[$userId], " +
                        "authorizedUserList=[${tokenInfo.authorizedUserList}], " +
                        "authorizedOrgList=[${tokenInfo.authorizedOrgList}], " +
                        "permission=[${permission.type}/${permission.action}], " +
                        "projectId=[${tokenInfo.projectId}], repoName=[${tokenInfo.repoName}], " +
                        "fullPath=[${tokenInfo.fullPath}]"
                )
            }
            return
        }
        delegate.onPermissionCheck(userId, permission)
    }

    /**
     * 取出当前请求绑定的临时 token 信息；不存在或上下文不可用时返回 null，回退到默认 ACL 链。
     */
    private fun currentTemporaryTokenInfo(): TemporaryTokenInfo? {
        return try {
            val request = HttpContextHolder.getRequest()
            request.getAttribute(PreviewTokenAuthService.REQ_ATTR_TEMP_TOKEN_INFO) as? TemporaryTokenInfo
        } catch (e: Exception) {
            logger.warn("PreviewArtifactPermissionCheck: failed to read request attribute, " +
                "fallback to default ACL", e)
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PreviewArtifactPermissionCheckHandler::class.java)
    }
}
