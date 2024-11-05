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

import com.tencent.bkrepo.analyst.pojo.license.SpdxLicenseInfo
import com.tencent.bkrepo.common.api.pojo.Page

/**
 * 许可证服务
 */
interface SpdxLicenseService {
    /**
     * 导入 SPDX 许可证列表
     * @param path SPDX 许可证列表 JSON 文件
     */
    fun importLicense(path: String): Boolean

    /**
     * 导入SPDX许可证列表
     *
     * @param projectId 许可证列表文件所在项目
     * @param repoName 许可证列表文件所在仓库
     * @param fullPath 许可证列表文件路径
     */
    fun importLicense(projectId: String, repoName: String, fullPath: String): Boolean

    /**
     * 分页查询许可证信息
     */
    fun listLicensePage(
        name: String?,
        isTrust: Boolean?,
        pageNumber: Int,
        pageSize: Int
    ): Page<SpdxLicenseInfo>

    /**
     * 查询所有许可证信息
     */
    fun listLicense(): List<SpdxLicenseInfo>

    /**
     * 查询许可证详细信息
     */
    fun getLicenseInfo(licenseId: String): SpdxLicenseInfo?

    /**
     * 根据许可证唯一标识切换合规状态
     */
    fun toggleStatus(licenseId: String)

    /**
     * 根据唯一标识集合查询许可证信息（scancode使用）
     */
    fun listLicenseByIds(licenseIds: List<String>): Map<String, SpdxLicenseInfo>
}
