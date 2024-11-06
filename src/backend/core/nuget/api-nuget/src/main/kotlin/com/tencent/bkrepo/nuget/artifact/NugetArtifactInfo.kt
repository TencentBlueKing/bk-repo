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

package com.tencent.bkrepo.nuget.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

open class NugetArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val NUGET_ROOT_URI = "/{projectId}/{repoName}"
        const val NUGET_ROOT_URI_V3 = "/{projectId}/{repoName}/v3"
        const val NUGET_PACKAGE_CONTENT_ROOT_URI = "/{projectId}/{repoName}/v3/flatcontainer"
        const val NUGET_RESOURCE = "ext/{projectId}/{repoName}/**"

        const val PUBLISH_V2 = "/v2/package"
        const val DOWNLOAD_V2 = "/Download/{id}/{version}"
        const val SEARCH_V2 = "/FindPackagesById()"
        const val DELETE_V2 = "/v2/package/{id}/{version}"

        const val DOWNLOAD_V3 = "/{id}/{version}/*.nupkg"
        const val DOWNLOAD_MANIFEST = "/{id}/{version}/{id}.nuspec"
        const val ENUMERATE_VERSIONS = "/{packageId}/index.json"

        const val SEARCH = "/query"

        const val SERVICE_INDEX = "/index.json"

        const val REGISTRATION_INDEX = "/registration/{id}/index.json"
        const val REGISTRATION_INDEX_FEATURE = "/registration{feature}/{id}/index.json"

        const val REGISTRATION_PAGE = "/registration/{id}/page/{lowerVersion}/{upperVersion}.json"
        const val REGISTRATION_PAGE_FEATURE = "/registration{feature}/{id}/page/{lowerVersion}/{upperVersion}.json"

        const val REGISTRATION_LEAF = "/registration/{id}/{version}.json"
        const val REGISTRATION_LEAF_FEATURE = "/registration{feature}/{id}/{version}.json"

        const val REGISTRATION_PAGE_PROXY = "/registration/proxy/page/{id}"
        const val REGISTRATION_PAGE_PROXY_FEATURE = "/registration{feature}/proxy/page/{id}"

        const val NUGET_EXT_DELETE_PACKAGE = "/package/delete/{projectId}/{repoName}"
        const val NUGET_EXT_DELETE_VERSION = "/version/delete/{projectId}/{repoName}"
        const val NUGET_EXT_VERSION_DETAIL = "/version/detail/{projectId}/{repoName}"
        const val NUGET_EXT_DOMAIN = "/address"
    }
}
