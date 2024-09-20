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

package com.tencent.bkrepo.conan.artifact.resolver

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.conan.constant.CHANNEL
import com.tencent.bkrepo.conan.constant.NAME
import com.tencent.bkrepo.conan.constant.PACKAGE_ID
import com.tencent.bkrepo.conan.constant.PACKAGE_REVISION
import com.tencent.bkrepo.conan.constant.PATH
import com.tencent.bkrepo.conan.constant.REVISION
import com.tencent.bkrepo.conan.constant.USERNAME
import com.tencent.bkrepo.conan.constant.VERSION
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

@Resolver(ConanArtifactInfo::class)
@Component
class ConanArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): ConanArtifactInfo {
        val attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
        val name = attributes[NAME]?.toString() ?: StringPool.UNDERSCORE
        val version = attributes[VERSION]?.toString() ?: StringPool.UNDERSCORE
        val userName = attributes[USERNAME]?.toString() ?: StringPool.UNDERSCORE
        val channel = attributes[CHANNEL]?.toString() ?: StringPool.UNDERSCORE
        val packageId = attributes[PACKAGE_ID]?.toString()
        val revision = attributes[REVISION]?.toString()
        val pRevision = attributes[PACKAGE_REVISION]?.toString()

        return ConanArtifactInfo(
            projectId = projectId,
            repoName = repoName,
            artifactUri = artifactUri,
            name = name,
            version = version,
            userName = userName,
            channel = channel,
            packageId = packageId,
            revision = revision,
            pRevision = pRevision,
        )
    }
}
