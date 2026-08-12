/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.config.properties

/**
 * Agent 模型上游认证方式。
 *
 * Consul / YAML 使用 kebab-case：`bk-gateway`、`api-key`。
 */
enum class AgentModelAuthType {
    /** 蓝鲸 API 网关：`X-Bkapi-Authorization` + bk_app_code/secret。 */
    BK_GATEWAY,

    /** OpenAI 兼容直连：`Authorization: Bearer {apiKey}`。 */
    API_KEY,
}
