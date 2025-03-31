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

package com.tencent.bkrepo.cargo.pojo.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

open class CargoArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
    val crateName: String? = null,
    val crateVersion: String? = null
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {

        const val PUBLISH = "/{projectId}/{repoName}/api/v1/crates/new"
        const val YANK = "/{projectId}/{repoName}/api/v1/crates/{crateName}/{version}/yank"
        const val UNYANK = "/{projectId}/{repoName}/api/v1/crates/{crateName}/{version}/unyank"
        const val OWNERS = "/{projectId}/{repoName}/api/v1/crates/{crateName}/owners"
        const val SEARCH = "/{projectId}/{repoName}/api/v1/crates"
        const val INDEX = "{projectId}/{repoName}/index/**"
        const val DOWNLOAD = "/{projectId}/{repoName}/api/v1/crates/{crateName}/{version}/download"

        // cargo version detail
        const val CARGO_VERSION_DETAIL = "/version/detail/{projectId}/{repoName}"

        // cargo delete
        const val CARGO_PACKAGE_DELETE_URL = "/package/delete/{projectId}/{repoName}"
        const val CARGO_VERSION_DELETE_URL = "/version/delete/{projectId}/{repoName}"
    }
}
