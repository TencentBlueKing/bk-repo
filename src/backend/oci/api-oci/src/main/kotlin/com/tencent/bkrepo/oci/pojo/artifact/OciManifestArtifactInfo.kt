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

package com.tencent.bkrepo.oci.pojo.artifact

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.oci.constant.DOCKER_DISTRIBUTION_MANIFEST_LIST_V2
import com.tencent.bkrepo.oci.util.OciLocationUtils

class OciManifestArtifactInfo(
    projectId: String,
    repoName: String,
    packageName: String,
    version: String,
    val reference: String,
    val isValidDigest: Boolean
) : OciArtifactInfo(projectId, repoName, packageName, version) {

    override fun getArtifactFullPath(): String {
        return if(getArtifactMappingUri().isNullOrEmpty()) {
            if (isValidDigest) {
                // PUT 请求时取 digest 做为tag名
                if (HttpContextHolder.getRequest().method.equals("PUT", ignoreCase = true)) {
                    OciLocationUtils.buildManifestPath(packageName, reference)
                } else {
                    OciLocationUtils.buildDigestManifestPathWithReference(packageName, reference)
                }
            } else {
                // 根据 Content-type 区分 manifest.json / list.manifest.json
                val mediaType = HeaderUtils.getHeader(HttpHeaders.CONTENT_TYPE)
                if (mediaType == DOCKER_DISTRIBUTION_MANIFEST_LIST_V2) {
                    OciLocationUtils.buildManifestListPath(packageName, reference)
                } else {
                    OciLocationUtils.buildManifestPath(packageName, reference)
                }
            }
        } else getArtifactMappingUri()!!

    }
}
