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

package com.tencent.bkrepo.pypi.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.pypi.api.PypiWebResource
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.service.PypiWebService
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.springframework.web.bind.annotation.RestController

@RestController
class PypiWebResourceController(
    private val pypiWebService: PypiWebService
) : PypiWebResource {
    override fun deletePackage(pypiArtifactInfo: PypiArtifactInfo, packageKey: String): Response<Void> {
        pypiWebService.deletePackage(pypiArtifactInfo, packageKey)
        return ResponseBuilder.success()
    }

    override fun deleteVersion(
        pypiArtifactInfo: PypiArtifactInfo,
        packageKey: String,
        version: String?
    ): Response<Void> {
        pypiWebService.delete(pypiArtifactInfo, packageKey, version)
        return ResponseBuilder.success()
    }

    override fun artifactDetail(
        pypiArtifactInfo: PypiArtifactInfo,
        packageKey: String,
        version: String?
    ): Response<Any?> {
        return ResponseBuilder.success(pypiWebService.artifactDetail(pypiArtifactInfo, packageKey, version))
    }

    override fun versionListPage(
        pypiArtifactInfo: PypiArtifactInfo,
        packageKey: String,
        pageNumber: Int,
        pageSize: Int
    ): Response<Page<PackageVersion>> {
        return ResponseBuilder.success(
            pypiWebService.versionListPage(
                pypiArtifactInfo,
                packageKey,
                pageNumber,
                pageSize
            )
        )
    }
}
