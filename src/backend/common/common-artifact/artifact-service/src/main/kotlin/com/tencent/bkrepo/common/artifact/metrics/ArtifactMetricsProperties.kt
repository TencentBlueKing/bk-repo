/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.metrics

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

@ConfigurationProperties("management.metrics")
data class ArtifactMetricsProperties(
    /**
     * 需要监控的仓库
     * */
    var includeRepositories: List<String> = emptyList(),

    /**
     * 最大meter数量
     * */
    var maxMeters: Int = -1,
    /**
     * 是否通过日志清洗获取传输指标数据
     */
    var collectByLog: Boolean = false,
    /**
     * 直方图le的最大值
     */
    var maxLe: Double = DataSize.ofMegabytes(100).toBytes().toDouble(),
    /**
     * 是否开启缓存使用情况统计
     */
    var enableArtifactCacheMetrics: Boolean = false,
    /**
     * 是否使用influxdb存储指标数据
     */
    var useInfluxDb: Boolean = true,
    /**
     * 页面host
     */
    var host: String = "",
    /**
     * 构建机agent列表
     */
    var builderAgentList: List<String> = emptyList(),
    /**
     * 客户端agent列表
     */
    var clientAgentList: List<String> = emptyList(),
    /**
     * web 平台账号id
     */
    var webPlatformId: String = "",
    /**
     * 超过该大小的文件未命中缓存时将计入大文件缓存未命中监控数据
     */
    var largeFileThreshold: DataSize = DataSize.ofGigabytes(3L),
    /**
     * 允许上报未知项目信息
     * */
    var allowUnknownProjectExport: Boolean = false,

    /**
     * 配置在在统计流量时固定在LruMeterFilter中不被淘汰的仓库
     */
    var pinnedRepositories: Set<String> = emptySet(),
)
