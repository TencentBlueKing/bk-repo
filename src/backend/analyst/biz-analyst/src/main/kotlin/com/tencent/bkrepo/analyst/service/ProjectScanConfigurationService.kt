/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.service

import com.tencent.bkrepo.analyst.pojo.ProjectScanConfiguration
import com.tencent.bkrepo.analyst.pojo.request.ProjectScanConfigurationPageRequest
import com.tencent.bkrepo.common.api.pojo.Page

/**
 * 项目扫描配置
 */
interface ProjectScanConfigurationService {
    /**
     * 创建项目扫描配置
     *
     * @param request 项目扫描配置创建请求
     *
     * @return 创建后的项目扫描配置
     */
    fun create(request: ProjectScanConfiguration): ProjectScanConfiguration

    /**
     * 删除项目扫描配置
     *
     * @param projectId 项目id
     */
    fun delete(projectId: String)

    /**
     * 更新项目扫描配置
     *
     * @param request 项目扫描配置更新请求
     *
     * @return 更新后的项目扫描配置
     */
    fun update(request: ProjectScanConfiguration): ProjectScanConfiguration

    /**
     * 分页获取项目扫描配置
     */
    fun page(request: ProjectScanConfigurationPageRequest): Page<ProjectScanConfiguration>

    /**
     * 获取项目扫描配置
     *
     * @param projectId 要获取的扫描配置所属项目ID
     *
     * @return 项目扫描配置
     */
    fun get(projectId: String): ProjectScanConfiguration

    /**
     * 获取项目或全局扫描配置
     *
     * @param projectId 项目id
     *
     * @return 项目扫描配置
     */
    fun findProjectOrGlobalScanConfiguration(projectId: String): ProjectScanConfiguration?
}
