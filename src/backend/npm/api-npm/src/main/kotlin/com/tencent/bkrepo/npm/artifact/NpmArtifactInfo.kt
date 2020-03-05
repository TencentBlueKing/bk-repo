package com.tencent.bkrepo.npm.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

class NpmArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
    val scope: String,
    val pkgName: String,
    val version: String
) : ArtifactInfo(projectId, repoName, artifactUri) {

    constructor(projectId: String, repoName: String, artifactUri: String) : this(
        projectId,
        repoName,
        artifactUri,
        "",
        "",
        ""
    )

    companion object {
        const val NPM_PKG_PUBLISH_MAPPING_URI = "/{projectId}/{repoName}/*"
        const val NPM_SCOPE_PKG_PUBLISH_MAPPING_URI = "/{projectId}/{repoName}/*/*"

        const val NPM_UNPUBLISH_MAPPING_URI = "/{projectId}/{repoName}/**/-rev/{rev}"

        // package uri model
        const val NPM_SCOPE_PACKAGE_VERSION_INFO_MAPPING_URI = "/{projectId}/{repoName}/{scope}/{pkgName}/{version}"
        const val NPM_PACKAGE_INFO_MAPPING_URI = "/{projectId}/{repoName}/{pkgName}"
        const val NPM_PACKAGE_VERSION_INFO_MAPPING_URI = "/{projectId}/{repoName}/{pkgName}/{version}"

        const val NPM_PACKAGE_TGZ_MAPPING_URI = "/{projectId}/{repoName}/{pkgName}/-/*.tgz"
        const val NPM_PACKAGE_SCOPE_SIMPLE_TGZ_MAPPING_URI = "/{projectId}/{repoName}/{scope}/{pkgName}/-/*.tgz"

        // search
        const val NPM_PACKAGE_SEARCH_MAPPING_URI = "/{projectId}/{repoName}/-/v1/search"

        // dist-tag
        const val NPM_PACKAGE_DIST_TAG_INFO_MAPPING_URI = "/{projectId}/{repoName}/-/package/**/dist-tags"
        const val NPM_PACKAGE_DIST_TAG_ADD_MAPPING_URI = "/{projectId}/{repoName}/-/package/**/dist-tags/*"

        // auth user
        const val NPM_ADD_USER_MAPPING_URI = "/{projectId}/{repoName}/-/user/org.couchdb.user:*"
        const val NPM_USER_LOGOUT_MAPPING_URI = "/{projectId}/{repoName}/-/user/token/*"
        const val NPM_WHOAMI_MAPPING_URI = "/{projectId}/{repoName}/-/whoami"
    }

    fun isValid(): Boolean {
        return pkgName.isNotBlank()
    }
}
