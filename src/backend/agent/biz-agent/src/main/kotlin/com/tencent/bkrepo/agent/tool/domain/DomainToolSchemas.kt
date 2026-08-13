/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.tool.domain

internal object DomainToolSchemas {

    val NO_ARGS: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any>(),
        "additionalProperties" to false,
    )

    fun obj(vararg properties: Pair<String, Map<String, Any>>, required: List<String> = emptyList()): Map<String, Any> {
        val schema = mutableMapOf<String, Any>(
            "type" to "object",
            "properties" to properties.toMap(),
            "additionalProperties" to false,
        )
        if (required.isNotEmpty()) {
            schema["required"] = required
        }
        return schema
    }

    fun str(description: String): Map<String, Any> = mapOf("type" to "string", "description" to description)
}
