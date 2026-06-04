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

/**
 * preview 微服务专用的 [PermissionCheckHandler]。
 *
 * 由于 [ArtifactPermissionCheckHandler] 是 final 类无法继承，这里采用 Kotlin **接口委托**：
 * 默认所有方法转发给被委托的 [delegate]，仅在 [onPermissionCheck] 入口加一层判定 ——
 * 若当前请求已通过 [PreviewTokenAuthHandler] 完成临时 token 鉴权
 * （request attribute 中存在 [PreviewTokenAuthHandler.REQ_ATTR_TEMP_TOKEN_INFO]），
 * 则直接放行；否则委托给原始的 [ArtifactPermissionCheckHandler] 走默认 ACL 校验链。
 *
 * 其他场景全部回退到被委托对象的默认行为，因此对 preview 服务的其他端点零侵入。
 */
class PreviewArtifactPermissionCheckHandler(
    private val delegate: ArtifactPermissionCheckHandler,
) : PermissionCheckHandler by delegate {

    override fun onPermissionCheck(userId: String, permission: Permission) {
        if (isTemporaryTokenAuthenticated()) return
        delegate.onPermissionCheck(userId, permission)
    }

    /**
     * 判断当前请求是否已通过临时 token 鉴权。
     * 只检查 preview 自家的 attribute key，避免污染其他逻辑。
     */
    private fun isTemporaryTokenAuthenticated(): Boolean {
        return try {
            val request = HttpContextHolder.getRequest()
            val tokenInfo = request.getAttribute(PreviewTokenAuthHandler.REQ_ATTR_TEMP_TOKEN_INFO)
            tokenInfo is TemporaryTokenInfo
        } catch (_: Exception) {
            false
        }
    }
}
