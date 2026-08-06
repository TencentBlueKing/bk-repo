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

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * preview 微服务专用的临时 token 鉴权配置。
 *
 * 通过 yaml 前缀 `preview.temporary-token` 绑定：
 *
 * ```yaml
 * preview:
 *   temporary-token:
 *     enabled: true
 * ```
 *
 * @property enabled 紧急关停开关：false 时 [PreviewTokenAuthService] 跳过阶段二 token 鉴权，
 *                   回退到现有登录态链路；默认 true。
 */
@ConfigurationProperties(prefix = "preview.temporary-token")
data class PreviewTokenAuthConfig(
    var enabled: Boolean = true,
)
