/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.config.properties

internal object AgentPropertiesRedaction {

    fun redactSecret(value: String): String = when {
        value.isBlank() -> "<unset>"
        value.length <= 4 -> "****"
        else -> "${value.take(2)}****${value.takeLast(2)}"
    }
}
