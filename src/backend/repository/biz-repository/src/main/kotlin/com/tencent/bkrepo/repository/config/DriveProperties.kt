package com.tencent.bkrepo.repository.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties("repository.drive")
@Component
class DriveProperties(
    /**
     * 是否启用 Drive 代理功能
     * 默认关闭，只有在需要时才开启
     */
    var enabled: Boolean = false,
    /**
     * ci 蓝鲸网关地址
     * 离岸环境为蓝盾离岸domain
     */
    var ciServer: String = "",
    /**
     * ci 服务认证bkAppCode
     */
    var bkAppCode: String = "",
    /**
     * ci 服务认证bkAppSecret
     */
    var bkAppSecret: String = "",
    /**
     * bk-drive 灰度标识
     */
    var gray: String = "",
    /**
     * 允许代理的接口白名单
     * Key: HTTP方法（GET, POST, PUT, DELETE等）
     * Value: 路径模式列表（支持Ant风格通配符）
     * 示例配置:
     * repository:
     *   drive:
     *     allowedApis:
     *       GET:
     *         - /api/drive/ci/user/info
     *         - /api/drive/ci/projects/{projectId}/
     *       POST:
     *         - /api/drive/ci/upload
     */
    var allowedApis: Map<String, List<String>> = emptyMap()
)
