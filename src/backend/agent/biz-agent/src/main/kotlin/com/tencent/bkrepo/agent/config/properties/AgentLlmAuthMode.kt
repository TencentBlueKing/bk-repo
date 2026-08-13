/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.config.properties

/**
 * Agent LLM 上游认证方式。
 *
 * 与 bk-ci 一致：`bkAppCode` 非空时使用蓝鲸网关，否则使用 API Key。
 */
enum class AgentLlmAuthMode {
    /** 蓝鲸 API 网关：`X-Bkapi-Authorization` + bk_app_code/secret。 */
    BK_GATEWAY,

    /** OpenAI 兼容直连：`Authorization: Bearer {apiKey}`。 */
    API_KEY,
}
