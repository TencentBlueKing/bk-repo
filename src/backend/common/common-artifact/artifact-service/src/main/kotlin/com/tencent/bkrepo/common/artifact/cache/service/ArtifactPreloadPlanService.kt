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

package com.tencent.bkrepo.common.artifact.cache.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import org.springframework.data.domain.PageRequest

interface ArtifactPreloadPlanService {
    /**
     * 根据预加载策略创建执行计划
     *
     * @param credentialsKey 缓存文件所在存储
     * @param sha256 缓存文件sha256
     */
    fun createPlan(credentialsKey: String?, sha256: String)

    /**
     * 删除指定预加载计划
     *
     * @param projectId 项目ID
     * @param repoName 仓库名
     * @param id 计划ID
     */
    fun deletePlan(projectId: String, repoName: String, id: String)

    /**
     * 删除指定项目仓库的所有预加载计划
     *
     * @param projectId 项目ID
     * @param repoName 仓库名
     */
    fun deletePlan(projectId: String, repoName: String)

    /**
     * 执行预加载计划
     */
    fun executePlans()

    /**
     * 分页获取预加载计划
     *
     * @param projectId 项目ID
     * @param repoName 仓库名
     * @param pageRequest 分页请求
     *
     * @return 预加载计划
     */
    fun plans(projectId: String, repoName: String, pageRequest: PageRequest): Page<ArtifactPreloadPlan>
}
