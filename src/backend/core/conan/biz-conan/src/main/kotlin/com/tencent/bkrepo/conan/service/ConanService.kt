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

package com.tencent.bkrepo.conan.service

import com.tencent.bkrepo.conan.pojo.IndexInfo
import com.tencent.bkrepo.conan.pojo.RevisionInfo
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo

/**
 * conan 请求相关接口
 */
interface ConanService {

    /**
     * 获取制品下CONAN_MANIFEST文件下载路径
     * 返回为key:value对,{filename : url}
     */
    fun getConanFileDownloadUrls(
        conanArtifactInfo: ConanArtifactInfo,
        subFileset: List<String> = emptyList()
    ): Map<String, String>

    /**
     * 获取package下CONAN_MANIFEST文件下载路径
     * 返回为key:value对,{filename : url}
     */
    fun getPackageDownloadUrls(
        conanArtifactInfo: ConanArtifactInfo,
        subFileset: List<String> = emptyList()
    ): Map<String, String>

    /**
     * 获取所有文件以及其md5
     * 返回为key:value对,{filename : md5}
     */
    fun getRecipeSnapshot(conanArtifactInfo: ConanArtifactInfo): Map<String, String>

    /**
     * 获取Package下所有文件以及其md5
     * 返回为key:value对,{filename : md5}
     */
    fun getPackageSnapshot(conanArtifactInfo: ConanArtifactInfo): Map<String, String>

    /**
     * 获取制品文件上传路径
     * 返回url
     */
    fun getConanFileUploadUrls(
        conanArtifactInfo: ConanArtifactInfo,
        fileSizes: Map<String, String>
    ): Map<String, String>

    /**
     * 获取package下文件上传路径
     * url
     */
    fun getPackageUploadUrls(
        conanArtifactInfo: ConanArtifactInfo,
        fileSizes: Map<String, String>
    ): Map<String, String>

    /**
     * 获取Package下所有文件列表
     */
    fun getPackageRevisionFiles(conanArtifactInfo: ConanArtifactInfo): Map<String, Map<String, String>>

    /**
     * 获取Package下所有文件列表
     */
    fun getRecipeRevisionFiles(conanArtifactInfo: ConanArtifactInfo): Map<String, Map<String, String>>

    /**
     * V2
     * 获取recipe下的revisions信息
     */
    fun getRecipeRevisions(conanArtifactInfo: ConanArtifactInfo): IndexInfo

    /**
     * V2
     * 获取recipe下的最新的revision信息
     */
    fun getRecipeLatestRevision(conanArtifactInfo: ConanArtifactInfo): RevisionInfo

    /**
     * V2
     * 获取package下的revisions信息
     */
    fun getPackageRevisions(conanArtifactInfo: ConanArtifactInfo): IndexInfo

    /**
     * V2
     * 获取package下的最新的revision信息
     */
    fun getPackageLatestRevision(conanArtifactInfo: ConanArtifactInfo): RevisionInfo
}
