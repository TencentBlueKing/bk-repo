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
import com.tencent.bkrepo.common.artifact.permission.ArtifactPermissionCheckHandler
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurityCustomizer
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.common.security.permission.PermissionCheckHandler
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * preview 微服务专用的安全配置：
 *  1. 注册 [PreviewTokenAuthHandler]：在 customizer 中关掉 common 默认的 TemporaryTokenAuthHandler，
 *     再插入 preview 自家的实现，把 PREVIEW token 完整范围校验链闭环在 preview 模块；
 *  2. 注册带 [Primary] 的 [PreviewArtifactPermissionCheckHandler]：覆盖 common-artifact 默认的
 *     [com.tencent.bkrepo.common.artifact.permission.ArtifactPermissionCheckHandler]，
 *     使得当请求已通过临时 token 鉴权时直接放行，不再走 ACL；
 *
 * common-security / common-artifact 不需要任何改动。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PreviewTokenAuthConfig::class)
class PreviewSecurityConfiguration {

    /**
     * 在 customizer 中关闭 common 默认的临时 token handler 注册，并替换为 preview 自家的实现。
     *
     * 时序保障：HttpAuthSecurityConfiguration.configHttpAuthSecurity 会先执行所有 customizer，
     * 再根据 `temporaryTokenEnabled` 标志位决定是否注册默认 handler。因此这里把标志位置 false
     * 后，common 端不会再添加默认 handler；同时本 customizer 直接调 addHttpAuthHandler 注入新实现。
     */
    @Bean
    fun previewHttpAuthSecurityCustomizer(
        authenticationManager: AuthenticationManager,
        temporaryTokenClient: ServiceTemporaryTokenClient,
        config: PreviewTokenAuthConfig,
    ): HttpAuthSecurityCustomizer = HttpAuthSecurityCustomizer { httpAuthSecurity ->
        // 阻止 common 端默认 TemporaryTokenAuthHandler 被注册
        httpAuthSecurity.temporaryTokenEnabled = false
        // 注入 preview 自己的临时 token handler
        httpAuthSecurity.addHttpAuthHandler(
            PreviewTokenAuthHandler(authenticationManager, temporaryTokenClient, config)
        )
    }

    /**
     * 通过 [Primary] 让 preview 自家的 PermissionCheckHandler 优先被注入到 PermissionAspect 中。
     *
     * 这里直接用 [PermissionManager] 自行 new 一个 [ArtifactPermissionCheckHandler] 作为 delegate，
     * 而不是从容器中按类型注入 — 因为 common-artifact 中以接口类型 [PermissionCheckHandler] 暴露
     * 该 bean，按具体类注入存在不确定性；并且自行 new 也能彻底避开 [Primary] 自我引用导致的歧义。
     */
    @Bean
    @Primary
    fun previewPermissionCheckHandler(permissionManager: PermissionManager): PermissionCheckHandler {
        val delegate = ArtifactPermissionCheckHandler(permissionManager)
        return PreviewArtifactPermissionCheckHandler(delegate)
    }
}
