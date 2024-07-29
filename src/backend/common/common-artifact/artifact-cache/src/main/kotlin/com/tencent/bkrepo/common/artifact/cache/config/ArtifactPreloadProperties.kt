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

package com.tencent.bkrepo.common.artifact.cache.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.time.Duration

/**
 * 缓存预加载配置
 */
@ConfigurationProperties("artifact.cache.preload")
data class ArtifactPreloadProperties(
    var enabled: Boolean = false,
    /**
     * 制品访问时间间隔，只有距离上次访问超过这个间隔时才会记录
     */
    var minAccessInterval: Duration = Duration.ofMinutes(30L),
    /**
     * 仅记录未命中缓存的记录
     */
    var onlyRecordCacheMiss: Boolean = true,
    /**
     * 只记录大小大于该值的文件
     */
    var minSize: DataSize = DataSize.ofGigabytes(1L),
    /**
     * 访问记录保留时间
     */
    var accessRecordKeepDuration: Duration = Duration.ofDays(7),
    /**
     * 单仓库最多创建的预加载策略数量，策略数量过多可能会导致生成预加载执行计划过慢
     */
    var maxStrategyCount: Int = 10,
    /**
     * 不允许预加载存在时间超超过这个值的制品，避免配置错误导致大量无用旧制品被加载到缓存中
     */
    var maxArtifactExistsDuration: Duration = Duration.ofDays(7L),
    /**
     * 执行计划超时时间，超时后将不再预加载
     */
    var planTimeout: Duration = Duration.ofHours(1L),
    /**
     * 允许同时预加载的制品个数
     */
    var preloadConcurrency: Int = 8,
    /**
     * 预加载策略未配置时间时使用的预加载时间，取值范围[0,24]，可配置多个，将选择离当前时间最近的一个作为预加载时间
     */
    var preloadHourOfDay: Set<Int> = emptySet(),
    /**
     * 减去随机时间，避免同时加载过多文件
     */
    var maxRandomSeconds: Long = 600L,
    /**
     * 根据sha256查询到的node数量超过该值时将不生成预加载计划，避免预加载计划创建时间过久
     */
    var maxNodes: Int = 10,
    /**
     * 是否仅模拟预加载，为true时不执行加载计划，仅输出一条日志
     */
    var mock: Boolean = false,
)
