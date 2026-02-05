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

package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.pojo.federation.FederationDiffStats
import com.tencent.bkrepo.replication.pojo.federation.FederationNodeCount
import com.tencent.bkrepo.replication.pojo.federation.FederationPathDiff
import com.tencent.bkrepo.replication.pojo.federation.request.FederationDiffStatsRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederationPathDiffRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryConfigRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryCreateRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryDeleteRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryUpdateRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederationDiffRequest

/**
 * 联邦仓库同步接口
 */
interface FederationRepositoryService {


    /**
     * 创建联邦仓库并分发配置
     */
    fun createFederationRepository(request: FederatedRepositoryCreateRequest): String

    /**
     * 在联邦集群上创建对应集群信息以及任务存储联邦仓库配置
     */
    fun saveFederationRepositoryConfig(request: FederatedRepositoryConfigRequest): Boolean

    /**
     * 根据项目id和仓库名称和federationId查询相关联的联邦仓库配置
     */
    fun listFederationRepository(
        projectId: String, repoName: String, federationId: String? = null,
    ): List<FederatedRepositoryInfo>

    /**
     * 删除联邦仓库配置（解散整个联邦）
     */
    fun deleteFederationRepositoryConfig(
        projectId: String,
        repoName: String,
        federationId: String,
        deleteRemote: Boolean = true
    )

    /**
     * 从联邦中移除指定集群
     */
    fun removeClusterFromFederation(request: FederatedRepositoryDeleteRequest)

    /**
     * 从联邦中移除指定集群（不解散联邦）
     */
    fun removeClusterFromFederation(
        projectId: String,
        repoName: String,
        federationId: String,
        remoteClusterName: String,
        remoteProjectId: String,
        remoteRepoName: String,
        deleteRemote: Boolean = true
    )

    /**
     * 根据项目仓库获取当前集群名
     */
    fun getCurrentClusterName(projectId: String, repoName: String, federationId: String): String?

    /**
     * 执行联邦仓库fullSync
     */
    fun fullSyncFederationRepository(projectId: String, repoName: String, federationId: String)

    /**
     * 更新联邦仓库配置
     */
    fun updateFederationRepository(request: FederatedRepositoryUpdateRequest): Boolean

    /**
     * 更新全量同步结束状态
     */
    fun updateFullSyncEnd(projectId: String, repoName: String, federationId: String)

    /**
     * 对比联邦仓库制品数量（最轻量级，只比较总数）
     *
     * 根据仓库类型智能选择对比策略：
     * - generic 仓库：对比节点总数
     * - 非 generic 仓库（maven、npm、docker 等）：对比 package 总数
     *
     * 推荐使用场景：
     * - 快速检查本地与远程仓库的制品数量是否一致
     * - 作为分层对比前的预检查
     *
     * @param request 差异对比请求
     * @return 节点/包数量对比结果列表（如果未指定目标集群，则返回与所有联邦集群的对比结果）
     */
    fun compareFederationNodeCount(request: FederationDiffRequest): List<FederationNodeCount>

    /**
     * 分层差异对比（推荐用于大数据量场景）
     *
     * 根据仓库类型和路径智能选择对比策略：
     * - generic 仓库：对比指定路径下的直接子节点，不递归深入
     * - 非 generic 仓库根路径 "/"：对比 package 列表及版本数量
     * - 非 generic 仓库子路径：对比节点（package version 下的文件）
     *
     * 每次只对比一层，数据量可控，不会 OOM。
     *
     * 推荐使用流程：
     * 1. 先调用 [compareFederationNodeCount] 快速检查总数是否一致
     * 2. 如不一致，调用本方法从根目录开始逐层对比
     * 3. 找到不一致的 package/目录后，递归调用本方法深入对比
     * 4. 最终定位到具体不一致的文件或版本
     *
     * @param request 分层对比请求
     * @return 该路径下的子节点/包差异列表
     */
    fun compareFederationPathDiff(request: FederationPathDiffRequest): FederationPathDiff

    /**
     * 多层目录聚合差异对比（推荐方案，一次请求获取多层统计）
     *
     * 通过聚合哈希快速判断目录是否一致，大幅减少请求次数。
     *
     * 优势：
     * - 一次请求获取多层目录统计（支持 1-3 层）
     * - 使用聚合哈希快速判断整个目录是否一致
     * - 即使文件数量相同，也能检测出内容不一致
     *
     * @param request 差异统计请求
     * @return 多层目录差异统计结果
     */
    fun compareFederationDiffStats(request: FederationDiffStatsRequest): FederationDiffStats

    /**
     * 智能差异对比（最推荐，自动选择最优策略）
     *
     * 该方法结合三种对比方式，提供最优的对比体验：
     * 1. 先快速对比总数
     * 2. 使用聚合哈希分层定位差异路径
     * 3. 自动收集不一致的路径列表
     *
     * @param projectId 项目ID
     * @param repoName 仓库名称
     * @param federationId 联邦ID
     * @param targetClusterId 目标集群ID
     * @param rootPath 起始路径
     * @param maxDepth 最大查询深度（默认3层）
     * @return 不一致的路径列表
     */
    fun smartCompareFederationDiff(
        projectId: String,
        repoName: String,
        federationId: String,
        targetClusterId: String,
        rootPath: String = "/",
        maxDepth: Int = 3
    ): List<String>

}