/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 */

package com.tencent.bkrepo.fs.server.pojo

import com.tencent.bkrepo.common.storage.pojo.RegionResource

/**
 * Drive 文件块元数据，供 preview 等微服务通过 Feign 拉取后本地读存储。
 */
data class DriveFileBlockInfo(
    val fullPath: String,
    val fileName: String,
    val size: Long,
    val blocks: List<RegionResource>,
)
