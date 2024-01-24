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

package com.tencent.bkrepo.fs.server.request

import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.springframework.util.AntPathMatcher
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.queryParamOrNull
import org.springframework.web.util.pattern.PathPattern

open class NodeRequest(
    open val projectId: String,
    open val repoName: String,
    open val fullPath: String,
    /**
     * 节点所在位置，LOCAL 或 REMOTE
     */
    open val category: String = RepositoryCategory.LOCAL.name,
    open val mode: Int? = null,
    open val flags: Int? = null,
    open val rdev: Int? = null,
    open val type: Int? = null,
) {
    constructor(request: ServerRequest) : this(
        projectId = request.pathVariable(PROJECT_ID),
        repoName = request.pathVariable(REPO_NAME),
        fullPath = resolveFullPath(request),
        category = request.queryParamOrNull("category") ?: RepositoryCategory.LOCAL.name,
        mode = request.queryParamOrNull("mode")?.toIntOrNull(),
        flags = request.queryParamOrNull("flags")?.toIntOrNull(),
        rdev = request.queryParamOrNull("rdev")?.toIntOrNull(),
        type = request.queryParamOrNull("type")?.toIntOrNull()
    )

    override fun toString(): String {
        return "$projectId/$repoName$fullPath"
    }

    companion object {
        private val antPathMatcher = AntPathMatcher()
        private fun resolveFullPath(request: ServerRequest): String {
            val encodeUrl = AntPathMatcher.DEFAULT_PATH_SEPARATOR + antPathMatcher.extractPathWithinPattern(
                (request.attribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE).get() as PathPattern).patternString,
                request.path()
            )
            val decodeUrl = URLDecoder.decode(encodeUrl, StandardCharsets.UTF_8.name())
            return PathUtils.normalizeFullPath(decodeUrl)
        }
    }
}
