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

package com.tencent.bkrepo.common.metadata.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties("repository")
class RepositoryProperties {
    var deletedNodeReserveDays: Long = 14
    var defaultStorageCredentialsKey: String? = null
    var listCountLimit: Long = 100000L
    var slowLogTimeThreshold: Long = 1_000
    /**
     * 节点 search 查询超时时间（毫秒），对应 MongoDB maxTimeMS
     */
    var nodeSearchQueryTimeoutMs: Long = 600_000
    @NestedConfigurationProperty
    var job: RepoJobProperties = RepoJobProperties()
    @NestedConfigurationProperty
    var repoStorageMapping: RepoStorageMapping = RepoStorageMapping()
    var allowUserAddSystemMetadata: List<String> = emptyList()
    var gitUrl: String = ""
    var svnUrl: String = ""
    /**
     * 用于验证bkci webhook签名
     */
    var bkciWebhookSecret: String = ""
    /**
     * 当目录节点上的num字段小于该值时，去db中实时count目录大小
     * 注意： 此配置的值要比listCountLimit大
     */
    var subNodeLimit: Long = 100000000L
    /**
     * 是否返回真实项目启用禁用状态
     */
    var returnEnabled: Boolean = true
    /**
     * 系统元数据标签
     */
    var systemMetadataLabels: List<String> = emptyList()
    /**
     * 更新流水线制品快照url
     */
    var updateArtifactUrl: String = ""
    /**
     * 更新流水线制品快照token
     */
    var updateArtifactToken: String = ""
    /**
     * 允许跳过项目禁用分享检查的平台账户白名单
     */
    var tokenBypassPlatforms: List<String> = emptyList()
    /**
     * 节点删除策略模式：
     * - update：直接 updateMulti，不使用 hint
     * - updateWithHint：updateMulti 时附带 hint 强制指定索引（需要 MongoDB 4.2+）
     * - batchByIds：分批从 Primary 通过 find（带 hint）查询节点 ID，再按 ID 批量 update，兼容 MongoDB 4.2 以下版本
     */
    var deleteMode: String = DELETE_MODE_UPDATE
    /**
     * 按项目配置删除模式，key 为 projectId，value 为对应的删除模式，未配置的项目使用全局 [deleteMode]
     */
    var projectDeleteMode: Map<String, String> = emptyMap()
    /**
     * batchByIds 模式下每批次的文档数量
     */
    var deleteBatchSize: Int = 200
    /**
     * 同时执行的 deleteNodes 操作数量上限，小于等于 0 表示不限制。
     * 超过上限的删除请求将快速失败（抛出 TooManyRequestsException）
     */
    var deleteNodesConcurrency: Int = 0
    /**
     * 单次删除操作允许影响的最大节点数，小于等于 0 表示不限制。
     * 删除前会先执行 count 查询，超过上限时直接拒绝
     */
    var maxDeleteNodeCount: Long = 0

    /**
     * 获取指定项目使用的节点删除策略模式，优先使用项目级配置 [projectDeleteMode]，未配置时回退到全局 [deleteMode]
     */
    fun getDeleteMode(projectId: String): String = projectDeleteMode[projectId] ?: deleteMode

    companion object {
        const val DELETE_MODE_UPDATE = "update"
        const val DELETE_MODE_UPDATE_WITH_HINT = "updateWithHint"
        const val DELETE_MODE_BATCH_BY_IDS = "batchByIds"
    }
}
