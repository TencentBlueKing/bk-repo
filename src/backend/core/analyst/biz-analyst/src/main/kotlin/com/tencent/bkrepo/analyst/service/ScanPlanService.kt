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

import com.tencent.bkrepo.analyst.pojo.ScanPlan
import com.tencent.bkrepo.analyst.pojo.request.ArtifactPlanRelationRequest
import com.tencent.bkrepo.analyst.pojo.request.PlanCountRequest
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.analyst.pojo.request.UpdateScanPlanRequest
import com.tencent.bkrepo.analyst.pojo.response.ArtifactPlanRelations
import com.tencent.bkrepo.analyst.pojo.response.ScanLicensePlanInfo
import com.tencent.bkrepo.analyst.pojo.response.ScanPlanInfo

/**
 * 扫描方案服务
 */
interface ScanPlanService {

    /**
     * 创建扫描方案
     */
    fun create(request: ScanPlan): ScanPlan

    /**
     * 获取扫描方案列表
     *
     * @param projectId 扫描方案所属项目
     * @param type 扫描方案类型
     * @param fileNameExt 文件名后缀，仅在type为GENERIC时有效
     *
     * @return 扫描方案列表
     */
    fun list(projectId: String, type: String? = null, fileNameExt: String? = null): List<ScanPlan>

    /**
     * 分页获取扫描方案列表
     *
     * @param projectId 扫描方案所属项目
     * @param type 扫描方案类型
     * @param planNameContains 扫描方案名包含的内容
     *
     * @return 扫描方案列表
     */
    fun page(
        projectId: String,
        type: String?,
        planNameContains: String?,
        pageLimit: PageLimit
    ): Page<ScanPlanInfo>

    /**
     * 获取扫描方案
     *
     * @param projectId 扫描方案所属项目id
     * @param id 扫描方案id
     *
     * @return 扫描方案
     */
    fun find(projectId: String, id: String): ScanPlan?

    /**
     * 获取扫描方案
     *
     * @param projectId 扫描方案所属项目id
     * @param type 扫描方案类型
     * @param name 扫描方案id
     *
     * @return 扫描方案
     */
    fun findByName(projectId: String, type: String, name: String): ScanPlan?

    /**
     * 获取[type]类型的默认扫描方案，不存在时则创建一个
     *
     * @param projectId 所属项目
     * @param type 默认扫描方案类型
     * @param scannerName 默认扫描方案使用的扫描器
     *
     * @return 默认扫描方案
     */
    fun getOrCreateDefaultPlan(
        projectId: String,
        type: String = RepositoryType.GENERIC.name,
        scannerName: String? = null
    ): ScanPlan

    /**
     * 删除扫描方案
     *
     * @param projectId 扫描方案所属项目id
     * @param id 扫描方案id
     */
    fun delete(projectId: String, id: String)

    /**
     * 更新扫描方案
     *
     * @param request 更新扫描方案请求
     *
     * @return 更新后的扫描方案
     */
    fun update(request: UpdateScanPlanRequest): ScanPlan

    /**
     * 获取扫描方案最新一次扫描详情
     *
     * @param request 获取扫描方案关联的统计请求，包含扫描方案信息和筛选条件
     *
     * @return 扫描方案最新一次扫描详情
     */
    fun scanPlanInfo(request: PlanCountRequest): ScanPlanInfo?

    /**
     * 获取制品关联的扫描方案列表
     *
     * @param request 获取制品关联的扫描方案请求，包含制品信息
     *
     * @return 制品关联的扫描方案信息
     */
    fun artifactPlanList(request: ArtifactPlanRelationRequest): ArtifactPlanRelations

    /**
     * 获取制品扫描状态
     *
     * @param request 制品信息
     *
     * @return 制品扫描状态
     */
    fun artifactPlanStatus(request: ArtifactPlanRelationRequest): String?

    /**
     * 获取扫描方案(license)最新一次扫描详情
     *
     * @param request 获取扫描方案关联的统计请求，包含扫描方案信息和筛选条件
     *
     * @return 扫描方案最新一次扫描详情
     */
    fun scanLicensePlanInfo(request: PlanCountRequest): ScanLicensePlanInfo?
}
