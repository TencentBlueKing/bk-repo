/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metrics.push.custom.enums

enum class TypeOfMetricsItem(
    val displayName: String, val help: String,
    val dataModel: DataModel, val keepHistory: Boolean
) {
    /**
     * 制品传输速率
     */
    ARTIFACT_TRANSFER_RATE(
        "artifact_transfer_rate",
        "artifact transfer rate",
        DataModel.DATAMODEL_GAUGE,
        false,
    ),

    /**
     * 制品传输大小（上传/下载）
     */
    ARTIFACT_TRANSFER_SIZE(
        "artifact_transfer_size_bytes",
        "artifact transfer size in bytes",
        DataModel.DATAMODEL_HISTOGRAM,
        false,
    ),

    /**
     * 制品带宽聚合指标（按项目/仓库/类型维度聚合的流量）
     * 定期上报后清理内存，避免维度爆炸
     *
     * 使用 Gauge 类型：每次上报的是"这个周期内的流量增量"
     * - 不是单调递增的 Counter
     * - 每个周期独立上报，上报后清零
     * - 监控端应使用 sum_over_time() 计算总流量，而非 rate()
     */
    ARTIFACT_BANDWIDTH(
        "artifact_bandwidth_bytes",
        "aggregated artifact bandwidth in bytes by project/repo/type (per interval)",
        DataModel.DATAMODEL_GAUGE,
        false,
    ),
}
