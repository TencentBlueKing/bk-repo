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

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.oci.constant.LAST_TAG
import com.tencent.bkrepo.oci.constant.N
import com.tencent.bkrepo.oci.pojo.artifact.OciTagArtifactInfo
import com.tencent.bkrepo.oci.pojo.response.CatalogResponse
import com.tencent.bkrepo.oci.service.OciCatalogService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OciCatalogServiceImpl : OciCatalogService {

    /**
     * n: declaring that the response should be limited to n results
     * last: last repository value from previous response
     */
    override fun getCatalog(artifactInfo: OciTagArtifactInfo, n: Int?, last: String?): CatalogResponse {
        logger.info(
            "Handling search catalog request for package " +
                "with n $n and last $last in repo [${artifactInfo.getRepoIdentify()}]"
        )
        val context = ArtifactQueryContext()
        last?.let { context.putAttribute(LAST_TAG, last) }
        n?.let { context.putAttribute(N, n) }
        val catalog = ArtifactContextHolder.getRepository().query(context)
            ?: return CatalogResponse(emptyList(), 0)
        return catalog as CatalogResponse
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OciCatalogServiceImpl::class.java)
    }
}
