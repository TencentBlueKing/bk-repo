/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.conan.service

import com.tencent.bkrepo.conan.pojo.request.IndexRefreshRequest

interface ConanExtService {

    /**
     * 重新生成整个仓库下所有的index.json文件
     */
    fun indexRefreshForRepo(projectId: String, repoName: String)

    /**
     * 重新生成整个指定key下所有的index.json文件
     */
    fun indexRefreshByPackageKey(projectId: String, repoName: String, key: String)

    /**
     * 重新生成recipe所有的index.json文件
     */
    fun indexRefreshForRecipe(projectId: String, repoName: String, request: IndexRefreshRequest)

    /**
     * 刷新整个仓库元数据信息
     */
    fun metadataRefresh(projectId: String, repoName: String)

    /**
     * 刷新整个仓库元数据信息
     */
    fun packageMetadataRefresh(projectId: String, repoName: String, packageKey: String)

    /**
     * 刷新整个仓库元数据信息
     */
    fun versionMetadataRefresh(projectId: String, repoName: String, packageKey: String, version: String)
}
