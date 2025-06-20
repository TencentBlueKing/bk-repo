/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.huggingface.artifact

import com.tencent.bkrepo.common.api.constant.StringPool.SLASH
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.huggingface.constants.REPO_TYPE_MODEL
import com.tencent.bkrepo.huggingface.constants.REVISION_KEY

open class HuggingfaceArtifactInfo(
    projectId: String,
    repoName: String,
    val organization: String,
    val name: String,
    private val revision: String?,
    val type: String?,
    private val artifactUri: String,
) : ArtifactInfo(projectId, repoName, artifactUri) {

    constructor(
        projectId: String,
        repoName: String,
        repoId: String,
        revision: String?,
        type: String?,
        artifactUri: String
    ) : this(projectId, repoName, repoId.split(SLASH)[0], repoId.split(SLASH)[1], revision, type, artifactUri)

    fun getRepoId(): String {
        return "$organization/$name"
    }

    open fun getRevision(): String? {
        val request = HttpContextHolder.getRequestOrNull()
        return request?.getAttribute(REVISION_KEY)?.toString() ?: revision
    }

    override fun getArtifactFullPath(): String {
        return "/$organization/$name/resolve/${getRevision()}$artifactUri"
    }

    override fun getArtifactVersion() = getRevision()

    fun getPackageKey() = PackageKeys.ofHuggingface(type ?: REPO_TYPE_MODEL, getRepoId())
}
