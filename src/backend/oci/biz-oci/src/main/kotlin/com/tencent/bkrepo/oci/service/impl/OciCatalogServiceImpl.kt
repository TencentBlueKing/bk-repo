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

package com.tencent.bkrepo.oci.service.impl

import com.tencent.bkrepo.common.artifact.config.ArtifactConfigurerSupport
import com.tencent.bkrepo.oci.pojo.response.CatalogResponse
import com.tencent.bkrepo.oci.service.OciCatalogService
import com.tencent.bkrepo.oci.util.OciUtils
import com.tencent.bkrepo.repository.api.PackageClient
import org.springframework.stereotype.Service

@Service
class OciCatalogServiceImpl(
    private val packageClient: PackageClient,
    private val artifactConfigurerSupport: ArtifactConfigurerSupport
) : OciCatalogService {

    /**
     * n: declaring that the response should be limited to n results
     * last: last repository value from previous response
     */
    override fun getCatalog(projectId: String, repoName: String, n: Int?, last: String?): CatalogResponse {
        val packageList = packageClient.listAllPackageNames(projectId, repoName).data.orEmpty()
        if (packageList.isEmpty()) return CatalogResponse()
        val nameList = mutableListOf<String>().apply {
            packageList.forEach {
                val packageName = OciUtils.getPackageNameFormPackageKey(
                    packageKey = it,
                    defaultType = artifactConfigurerSupport.getRepositoryType(),
                    extraTypes = artifactConfigurerSupport.getRepositoryTypes()
                )
                this.add(packageName)
            }
            this.sort()
        }
        val imageList = OciUtils.filterHandler(
            tags = nameList,
            n = n,
            last = last
        )
        return CatalogResponse(imageList)
    }
}
