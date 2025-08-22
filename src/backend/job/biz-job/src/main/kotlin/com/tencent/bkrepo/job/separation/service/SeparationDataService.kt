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

package com.tencent.bkrepo.job.separation.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import java.time.LocalDateTime

interface SeparationDataService {

    fun findNodeInfo(
        projectId: String,
        repoName: String,
        fullPath: String,
    ): NodeInfo?

    fun findPackageVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String,
    ): PackageVersion?

    /**
     * 从冷数据中分页查询节点
     */
    fun listNodePage(
        projectId: String,
        repoName: String,
        fullPath: String,
        option: NodeListOption,
        separationDate: LocalDateTime,
    ): Page<NodeInfo>

    /**
     * 从冷数据中分页查询包列表, 支持根据packageName模糊搜索
     *
     * @param projectId 项目id
     * @param repoName 仓库id
     * @param option 包列表选项
     */
    fun listPackagePage(
        projectId: String,
        repoName: String,
        option: PackageListOption,
    ): Page<PackageSummary>

    /**
     * 从冷数据中分页查询版本列表
     *
     * @param projectId 项目id
     * @param repoName 仓库id
     * @param packageKey 包唯一标识
     * @param option 列表选项
     */
    fun listVersionPage(
        projectId: String,
        repoName: String,
        packageKey: String,
        option: VersionListOption,
        separationDate: LocalDateTime,
    ): Page<PackageVersion>
}