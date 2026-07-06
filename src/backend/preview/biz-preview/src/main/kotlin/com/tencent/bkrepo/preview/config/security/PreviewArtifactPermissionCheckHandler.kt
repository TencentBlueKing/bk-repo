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
 * 若当前请求已通过 [PreviewTokenAuthHandler] 完成临时 token 鉴权
 * （request attribute 中存在 [PreviewTokenAuthHandler.REQ_ATTR_TEMP_TOKEN_INFO]），
 * 则按 token 是否定向分享走两条不同的安全路径：
 *
 *  1. **匿名分享**（`authorizedUserList` 为空，任何拿到 token 的人都能访问）：
 *     用 `tokenInfo.createdBy` 重新走一遍默认 ACL 校验链，确保
 *     - token 创建者权限被回收 / 仓库改为私有时，token 立即失效；
 *     - 越过 token.fullPath 的请求会被路径不匹配 ([PreviewTokenAuthHandler.validateTokenScope]) 拦截，
 *       作为兜底再由 ACL 检查节点级权限；
 *     - 服务请求豁免、PERSONAL 仓豁免、project/repo enabled 等通用语义复用 common 实现。
 *
 *  2. **定向分享**（`authorizedUserList` 非空，仅授权用户可访问）：
 *     **直接放行 ACL 校验**，因为：
 *     - token 创建者已在 [PreviewTokenAuthHandler.bindUserAndAudit] 中主动把资源授权给指定用户，
 *       这是显式的、有审计的"权限委托"动作，等同于 generic `TemporaryAccessService` 的临时 token 语义；
 *     - 访问者的真实身份 (`gatewayUid`) 已通过 `gatewayUid in authorizedUserList` 这道身份关卡校验过；
 *     - 若再用访问者自身 ACL 卡校验，会让"管理员把仅自己有权限的文件分享给普通用户预览"的
 *       常见业务场景失效；若再用 createdBy ACL 卡校验，又会因 createdBy 当时上下文（无 cookie/无 platformId）
 *       下的远程鉴权差异导致预期外的 403。
 *     - 资源边界仍由 [PreviewTokenAuthHandler.validateTokenScope]（projectId/repoName/fullPath/expireDate/permits/IP）
 *       兜住，不会扩大 token 设计的访问范围。
 *
 * 其他场景全部回退到被委托对象的默认行为，因此对 preview 服务的其他端点零侵入。
 */
class PreviewArtifactPermissionCheckHandler(
    private val delegate: ArtifactPermissionCheckHandler,
) : PermissionCheckHandler by delegate {

    override fun onPermissionCheck(userId: String, permission: Permission) {
        val tokenInfo = currentTemporaryTokenInfo()
        if (tokenInfo != null) {
            if (tokenInfo.authorizedUserList.isEmpty()) {
                // 匿名分享：以 token 创建者身份再走一次默认 ACL 校验，
                // 防止"创建者权限被回收 / 仓库改私有 / 路径越界"等场景下 token 仍可被滥用
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
            // 定向分享：信任 createdBy 的主动授权 + 已校验过的访问者身份，
            // 资源边界由 token scope 兜住，这里直接放行 ACL，对齐 generic 临时 token 行为
            if (logger.isInfoEnabled) {
                logger.info(
                    "PreviewArtifactPermissionCheck(directed-share) bypass ACL: " +
                        "createdBy=${tokenInfo.createdBy}, visitor=$userId, " +
                        "authorizedUserList=${tokenInfo.authorizedUserList}, " +
                        "permission=${permission.type}/${permission.action}, " +
                        "projectId=${tokenInfo.projectId}, repoName=${tokenInfo.repoName}, " +
                        "fullPath=${tokenInfo.fullPath}"
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
            request.getAttribute(PreviewTokenAuthHandler.REQ_ATTR_TEMP_TOKEN_INFO) as? TemporaryTokenInfo
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
