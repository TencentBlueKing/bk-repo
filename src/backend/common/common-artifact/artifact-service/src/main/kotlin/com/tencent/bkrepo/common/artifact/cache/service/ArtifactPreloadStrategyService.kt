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

import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategyCreateRequest
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategy
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategyUpdateRequest

interface ArtifactPreloadStrategyService {
    /**
     * 创建预加载策略
     *
     * @param request 策略
     *
     * @return 创建后的策略
     */
    fun create(request: ArtifactPreloadStrategyCreateRequest): ArtifactPreloadStrategy

    /**
     * 更新预加载策略
     *
     * @param request 策略
     * @param
     */
    fun update(request: ArtifactPreloadStrategyUpdateRequest): ArtifactPreloadStrategy

    /**
     * 删除策略
     *
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param id 策略id
     */
    fun delete(projectId: String, repoName: String, id: String? = null)

    /**
     * 获取预加载策略
     *
     * @param projectId 策略所属项目
     * @param repoName 仓库名
     *
     * @return 策略列表
     */
    fun list(projectId: String, repoName: String): List<ArtifactPreloadStrategy>
}
