/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.cache.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 制品预加载策略，用于将制品提前加载到缓存提高下载速度
 */
@Document("artifact_preload_strategy")
@CompoundIndexes(
    CompoundIndex(name = "projectId_repoName_idx", def = "{'projectId': 1, 'repoName': 1}", background = true)
)
data class TArtifactPreloadStrategy(
    val id: String? = null,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,

    /**
     * 策略所属项目
     */
    val projectId: String,
    /**
     * 策略所属仓库
     */
    val repoName: String,
    /**
     * 文件路径正则，匹配成功才会执行预加载
     */
    val fullPathRegex: String,
    /**
     * 仅加载大于指定大小的文件
     */
    val minSize: Long,
    /**
     * 限制只对最近一段时间内创建的制品执行预加载
     */
    val recentSeconds: Long,
    /**
     * 预加载执行时间，CUSTOM、CUSTOM_GENERATED类型时该字段有值
     */
    val preloadCron: String? = null,
    /**
     * 策略类型
     */
    val type: String,
)
