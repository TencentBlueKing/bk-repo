package com.tencent.bkrepo.composer.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 *
 * @author: carrypan
 * @date: 2019/12/2
 */
class ComposerArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
    val packageName: String?,
    val version: String?,
    val metadata: Map<String, String>?
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val COMPOSER_DEPLOY = "/{projectId}/{repoName}/{artifactUri}"
        const val COMPOSER_INSTALL = "/{projectId}/{repoName}/**"
        const val COMPOSER_PACKAGES = "/{projectId}/{repoName}/packages.json"
        const val COMPOSER_SEARCH = "/{projectId}/{repoName}/search.json?q={query}&type={type}"
    }
}
