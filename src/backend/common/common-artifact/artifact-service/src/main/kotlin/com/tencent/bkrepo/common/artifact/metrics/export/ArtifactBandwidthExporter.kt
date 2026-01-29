/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.metrics.export

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.metrics.ArtifactBandwidthRecord
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetricsProperties
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.metrics.push.custom.CustomMetricsExporter
import com.tencent.bkrepo.common.metrics.push.custom.base.MetricsItem
import com.tencent.bkrepo.common.metrics.push.custom.enums.TypeOfMetricsItem
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/**
 * 流量聚合导出器
 *
 * 按维度（项目/仓库/类型）聚合流量数据，定期主动上报后清理内存。
 * 解决 Micrometer Counter 维度爆炸导致只能统计部分项目的问题。
 *
 * 使用方式：
 * 1. 初始化时调用 [init] 设置配置
 * 2. 在数据流传输过程中调用 [Companion.record] 静态方法记录流量
 * 3. 定期调用 [export] 方法导出并清理数据
 */
class ArtifactBandwidthExporter(
    customMetricsExporter: CustomMetricsExporter? = null,
    properties: ArtifactMetricsProperties,
) {

    init {
        Companion.customMetricsExporter = customMetricsExporter
        Companion.allowUnknownProjectExport = properties.allowUnknownProjectExport
        Companion.enabled = properties.enableBandwidthAggregation
    }

    /**
     * 导出并清理聚合数据
     *
     * 将当前聚合的所有流量数据导出到监控系统，然后清理内存。
     * 此方法应该被定时调用（如每30秒一次）。
     *
     * 采用双缓冲交换策略：
     * 1. 原子地将当前 bandwidthMap 替换为新的空 map
     * 2. 对旧 map 中的数据进行导出
     * 3. 旧 map 被 GC 回收
     *
     * 这种方式保证：
     * - 正在进行的 record() 调用会完整写入（要么写入旧 map，要么写入新 map）
     * - 不会丢失任何数据
     */
    fun export() {
        if (!enabled) {
            return
        }

        // 原子交换，获取旧的 map 进行导出
        val toExport = swapAndGet()

        if (toExport.isEmpty()) {
            return
        }

        logger.info("Exporting [${toExport.size}] aggregated bandwidth records")

        // 导出到监控系统
        toExport.forEach { (key, adder) ->
            try {
                val bytes = adder.sum()
                if (bytes <= 0) return@forEach

                val (projectId, repoName, type) = ArtifactBandwidthRecord.parseKey(key)
                val labels = mutableMapOf(
                    ArtifactBandwidthRecord.LABEL_PROJECT_ID to projectId,
                    ArtifactBandwidthRecord.LABEL_REPO_NAME to repoName,
                    ArtifactBandwidthRecord.LABEL_TYPE to type
                )
                val metrics = TypeOfMetricsItem.ARTIFACT_BANDWIDTH
                val metricsItem = MetricsItem(
                    metrics.displayName,
                    metrics.help,
                    metrics.dataModel,
                    metrics.keepHistory,
                    bytes.toDouble(),
                    labels
                )
                customMetricsExporter?.reportMetrics(metricsItem)
            } catch (e: Exception) {
                logger.warn("Failed to export bandwidth record for key [$key]", e)
            }
        }
    }

    /**
     * 获取当前聚合的维度数量（用于监控）
     */
    fun getDimensionCount(): Int = bandwidthMap.size

    /**
     * 清理所有数据（用于测试或紧急情况）
     */
    fun clear() {
        bandwidthMap.clear()
    }

    @Suppress("LateinitUsage")
    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactBandwidthExporter::class.java)

        /**
         * 流量聚合存储（使用 @Volatile 保证可见性）
         * key: "projectId:repoName:type"
         * value: 聚合的字节数
         *
         * 采用双缓冲交换策略：导出时原子地替换整个 map，避免并发问题
         */
        @Volatile
        private var bandwidthMap = ConcurrentHashMap<String, LongAdder>()

        private var customMetricsExporter: CustomMetricsExporter? = null
        private var allowUnknownProjectExport: Boolean = false
        private var enabled: Boolean = false

        /**
         * 记录上传流量（静态方法，在数据流传输过程中调用）
         * 自动从 ArtifactContextHolder 获取项目和仓库信息
         *
         * @param bytes 传输字节数
         */
        fun recordUpload(bytes: Int) {
            if (!enabled || bytes <= 0) return
            val repositoryDetail = ArtifactContextHolder.getRepoDetailOrNull()
            val projectId = repositoryDetail?.projectId ?: StringPool.UNKNOWN
            val repoName = repositoryDetail?.name ?: StringPool.UNKNOWN
            record(projectId, repoName, ArtifactBandwidthRecord.TYPE_RECEIVE, bytes.toLong())
        }

        /**
         * 记录下载流量（静态方法，在数据流传输过程中调用）
         * 自动从 ArtifactContextHolder 获取项目和仓库信息
         *
         * @param bytes 传输字节数
         */
        fun recordDownload(bytes: Int) {
            if (!enabled || bytes <= 0) return
            val repositoryDetail = ArtifactContextHolder.getRepoDetailOrNull()
            val projectId = repositoryDetail?.projectId ?: StringPool.UNKNOWN
            val repoName = repositoryDetail?.name ?: StringPool.UNKNOWN
            record(projectId, repoName, ArtifactBandwidthRecord.TYPE_RESPONSE, bytes.toLong())
        }

        /**
         * 记录流量
         *
         * @param projectId 项目ID
         * @param repoName 仓库名称
         * @param type 传输类型（RECEIVE/RESPONSE）
         * @param bytes 传输字节数
         */
        private fun record(projectId: String, repoName: String, type: String, bytes: Long) {
            if ((projectId == StringPool.UNKNOWN || repoName == StringPool.UNKNOWN) && !allowUnknownProjectExport) {
                return
            }
            val key = ArtifactBandwidthRecord.buildKey(projectId, repoName, type)
            // 注意：这里获取的是当前的 bandwidthMap 引用
            // 即使在 export() 中被替换，正在进行的 add() 也会完成到旧 map 中
            bandwidthMap.computeIfAbsent(key) { LongAdder() }.add(bytes)
        }

        /**
         * 交换并获取当前的 bandwidthMap（用于导出）
         * 使用双缓冲策略，避免导出时的并发问题
         *
         * @return 旧的 bandwidthMap，包含待导出的数据
         */
        @Synchronized
        fun swapAndGet(): ConcurrentHashMap<String, LongAdder> {
            val current = bandwidthMap
            bandwidthMap = ConcurrentHashMap()
            return current
        }
    }
}

