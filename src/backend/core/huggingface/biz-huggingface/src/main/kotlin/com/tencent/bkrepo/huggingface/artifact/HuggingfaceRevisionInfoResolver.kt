/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.huggingface.artifact

import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.huggingface.constants.NAME_KEY
import com.tencent.bkrepo.huggingface.constants.ORGANIZATION_KEY
import com.tencent.bkrepo.huggingface.constants.REVISION_KEY
import com.tencent.bkrepo.huggingface.constants.TYPE_KEY
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

@Component
@Resolver(HuggingfaceRevisionInfo::class)
class HuggingfaceRevisionInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): HuggingfaceRevisionInfo {
        val attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
        val type = attributes[TYPE_KEY]!!.toString()
        val organization = attributes[ORGANIZATION_KEY]!!.toString()
        val name = attributes[NAME_KEY]!!.toString()
        val revision = attributes[REVISION_KEY]?.toString()
        return HuggingfaceRevisionInfo(projectId, repoName, organization, name, revision, type, artifactUri)
    }
}
