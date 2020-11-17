/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest

/**
 * 包服务类接口
 */
interface PackageService {

    /**
     * 根据包名查询包信息
     *
     * @param projectId 项目id
     * @param repoName 仓库名称
     * @param packageKey 包唯一标识
     */
    fun findPackageByKey(
        projectId: String,
        repoName: String,
        packageKey: String
    ): PackageSummary?

    /**
     * 查询版本信息
     *
     * @param projectId 项目id
     * @param repoName 仓库名称
     * @param packageKey 包唯一标识
     * @param versionName 版本名称
     */
    fun findVersionByName(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String
    ): PackageVersion?

    /**
     * 分页查询包列表, 根据packageName模糊搜索
     *
     * @param projectId 项目id
     * @param repoName 仓库id
     * @param packageName 包名称
     * @param pageNumber 页码
     * @param pageSize 每页数量
     */
    fun listPackagePageByName(
        projectId: String,
        repoName: String,
        packageName: String? = null,
        pageNumber: Int,
        pageSize: Int
    ): Page<PackageSummary>

    /**
     * 查询版本列表
     *
     * @param projectId 项目id
     * @param repoName 仓库id
     * @param packageKey 包唯一标识
     * @param versionName 版本名称，前缀匹配
     * @param stageTag 晋级标签过滤
     * @param pageNumber 页码
     * @param pageSize 每页数量
     */
    fun listVersionPage(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String? = null,
        stageTag: List<String>? = null,
        pageNumber: Int,
        pageSize: Int
    ): Page<PackageVersion>

    /**
     * 创建包版本
     * 如果包不存在，会自动创建包
     *
     * @param request 包版本创建请求
     */
    fun createPackageVersion(request: PackageVersionCreateRequest)

    /**
     * 删除包
     *
     * @param projectId 项目id
     * @param repoName 项目id
     * @param packageKey 包唯一标识
     */
    fun deletePackage(
        projectId: String,
        repoName: String,
        packageKey: String
    )

    /**
     * 删除包版本
     *
     * @param projectId 项目id
     * @param repoName 项目id
     * @param packageKey 包唯一标识
     * @param versionName 版本名称
     */
    fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String
    )

    /**
     * 下载包版本
     *
     * @param projectId 项目id
     * @param repoName 项目id
     * @param packageKey 包唯一标识
     * @param versionName 版本名称
     */
    fun downloadVersion(projectId: String, repoName: String, packageKey: String, versionName: String)

    /**
     * 包下载数量统计
     *
     * @param projectId 项目id
     * @param repoName 项目id
     * @param packageKey 包唯一标识
     * @param versionName 版本名称
     */
    fun addDownloadMetric(projectId: String, repoName: String, packageKey: String, versionName: String)

    /**
     * 根据[queryModel]搜索包
     */
    fun searchPackage(queryModel: QueryModel): Page<MutableMap<*, *>>
}
