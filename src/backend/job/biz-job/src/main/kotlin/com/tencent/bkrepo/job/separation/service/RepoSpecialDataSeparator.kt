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

package com.tencent.bkrepo.job.separation.service

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.job.separation.pojo.VersionSeparationInfo

/**
 * 不同依赖源特有数据拆分与恢复
 */
interface RepoSpecialDataSeparator {
    /**
     * 匹配仓库类型
     */
    fun type(): RepositoryType

    /**
     * 额外兼容仓库类型
     * (现主要是因为使用oci替换docker，需要兼容)
     */
    fun extraType(): RepositoryType?

    /**
     * 根据版本信息获取对应node列表
     */
    fun getNodesOfVersion(
        versionSeparationInfo: VersionSeparationInfo, accessCheck: Boolean = true
    ): MutableMap<String, String>

    /**
     * 根据条件处理对应类型仓库特定冷数据
     */
    fun separateRepoSpecialData(versionSeparationInfo: VersionSeparationInfo)

    /**
     * 根据条件删除对应类型仓库特定冷数据
     */
    fun removeRepoSpecialData(versionSeparationInfo: VersionSeparationInfo)

    /**
     * 根据版本信息获取对应node列表
     */
    fun getRestoreNodesOfVersion(versionSeparationInfo: VersionSeparationInfo): MutableMap<String, String>

    /**
     * 根据条件恢复对应类型仓库特定冷数据
     */
    fun restoreRepoSpecialData(versionSeparationInfo: VersionSeparationInfo)

    /**
     * 根据条件删除已恢复的仓库特定冷数据
     */
    fun removeRestoredRepoSpecialData(versionSeparationInfo: VersionSeparationInfo)
}
