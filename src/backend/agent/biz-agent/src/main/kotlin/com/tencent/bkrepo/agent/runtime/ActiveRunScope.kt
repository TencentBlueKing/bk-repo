/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.runtime

/** 运行保障 scope：userId + projectId + threadId。 */
data class ActiveRunScope(
    val userId: String,
    val projectId: String,
    val threadId: String,
)
