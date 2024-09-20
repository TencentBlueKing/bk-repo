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

package com.tencent.bkrepo.conan.pojo.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

class ConanArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
    val name: String,
    val version: String,
    val userName: String,
    val channel: String,
    var packageId: String?,
    var revision: String? = null,
    var pRevision: String? = null,
    var fileName: String? = null
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {

        // TODO 路径优化
        // ping
        const val PING_V1 = "/{projectId}/{repoName}/v1/ping"

        // check_credentials
        const val CHECK_CREDENTIALS_V1 = "/{projectId}/{repoName}/v1/users/check_credentials"

        // authenticate
        const val AUTHENTICATE_V1 = "/{projectId}/{repoName}/v1/users/authenticate"

        // search
        const val SEARCH_V1 = "/{projectId}/{repoName}/v1/conans/search"

        // package search
        const val PACKAGE_SEARCH_V1 = "/{projectId}/{repoName}/v1/conans/" +
            "{name}/{version}/{username}/{channel}/search"

        // get recipe manifest
        const val GET_RECIPE_MANIFEST_V1 = "/{projectId}/{repoName}/v1/conans/" +
            "{name}/{version}/{username}/{channel}/digest"

        // get recipe snapshot
        const val GET_RECIPE_SNAPSHOT_V1 = "/{projectId}/{repoName}/v1/conans/{name}/{version}/{username}/{channel}"

        // get package manifest
        const val GET_PACKAGE_MANIFEST_V1 = "/{projectId}/{repoName}/v1/conans/" +
            "{name}/{version}/{username}/{channel}/packages/{packageId}/digest"

        // get package snapshot
        const val GET_PACKAGE_SNAPSHOT_V1 = "/{projectId}/{repoName}/v1/conans/" +
            "{name}/{version}/{username}/{channel}/packages/{packageId}"

        // get recipe upload urls
        const val GET_RECIPE_UPLOAD_URLS_V1 = "/{projectId}/{repoName}/v1/conans/" +
            "{name}/{version}/{username}/{channel}/upload_urls"

        // get package download urls
        const val GET_PACKAGE_UPLOAD_URLS_V1 = "/{projectId}/{repoName}/v1/conans/" +
            "{name}/{version}/{username}/{channel}/packages/{packageId}/upload_urls"

        // get conan file download urls
        const val GET_CONANFILE_DOWNLOAD_URLS_V1 = "/{projectId}/{repoName}/v1/conans/" +
            "{name}/{version}/{username}/{channel}/download_urls"

        // get package download urls
        const val GET_PACKAGE_DOWNLOAD_URLS_V1 = "/{projectId}/{repoName}/v1/conans/" +
            "{name}/{version}/{username}/{channel}/packages/{packageId}/download_urls"

        // upload file
        const val UPLOAD_FILE_V1 = "/{projectId}/{repoName}/v1/files/" +
            "{username}/{name}/{version}/{channel}/{revision}/export/{path}"
        const val UPLOAD_PACKAGE_FILE_V1 = "/{projectId}/{repoName}/v1/files/" +
            "{username}/{name}/{version}/{channel}/{revision}/package/{packageId}/{pRevision}/{path}"

        // remove recipe
        const val REMOVE_RECIPE_V1 = "/{projectId}/{repoName}/v1/conans/{name}/{version}/{username}/{channel}"

        // remove packages
        const val REMOVE_PACKAGES_V1 = "/{projectId}/{repoName}/v1/conans/" +
            "{name}/{version}/{username}/{channel}/packages/delete"

        // remove recipe files
        const val REMOVE_FILES_V1 = "/{projectId}/{repoName}/v1/conans/" +
            "{name}/{version}/{username}/{channel}/remove_files"

        // V2
        // get package file list
        const val GET_PACKAGE_REVISION_FILES_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/revisions/{revision}" +
            "/packages/{packageId}/revisions/{pRevision}/files"

        // package file
        const val PACKAGE_REVISION_FILE_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/revisions/{revision}" +
            "/packages/{packageId}/revisions/{pRevision}/files/**"

        // get recipe file list
        const val GET_RECIPE_REVISION_FILES_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/revisions/{revision}/files"

        // recipe file
        const val RECIPE_REVISION_FILE_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/revisions/{revision}/files/**"

        // get recipe revisions
        const val RECIPE_REVISIONS_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/revisions"

        // get recipe latest
        const val RECIPE_LATEST_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/latest"

        // get recipe index.json
        const val RECIPE_INDEX = "/{projectId}/{repoName}/" +
            "{username}/{name}/{version}/{channel}/index.json"

        // get package index.json
        const val PACKAGE_INDEX = "/{projectId}/{repoName}/" +
            "{username}/{name}/{version}/{channel}/{revision}/package/{packageId}/index.json"

        // get package revisions
        const val PACKAGE_REVISIONS_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/revisions/{revision}" +
            "/packages/{packageId}/revisions"

        // get package latest
        const val PACKAGE_LATEST_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/revisions/{revision}" +
            "/packages/{packageId}/latest"

        // search
        const val SEARCH_V2 = "/{projectId}/{repoName}/v2/conans/search"

        const val REVISION_SEARCH_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/revisions/{revision}/search"
        // package search
        const val PACKAGE_SEARCH_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/search"

        // check_credentials
        const val CHECK_CREDENTIALS_V2 = "/{projectId}/{repoName}/v2/users/check_credentials"

        // authenticate
        const val AUTHENTICATE_V2 = "/{projectId}/{repoName}/v2/users/authenticate"

        // remove recipe
        const val REMOVE_RECIPE_V2 = "/{projectId}/{repoName}/v2/conans/{name}/{version}/{username}/{channel}"
        const val REMOVE_RECIPE_REVISIONS_V2 =
            "/{projectId}/{repoName}/v2/conans/{name}/{version}/{username}/{channel}/revisions/{revision}"

        // remove packages
        const val REMOVE_PACKAGE_RECIPE_REVISION_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/revisions/{revision}/packages/{packageId}"
        const val REMOVE_PACKAGE_REVISION_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/revisions/{revision}/packages/{packageId}/revisions/{pRevision}"

        // remove all packages in revision
        const val REMOVE_ALL_PACKAGE_UNDER_REVISION_V2 = "/{projectId}/{repoName}/v2/conans/" +
            "{name}/{version}/{username}/{channel}/revisions/{revision}/packages"
    }
}
