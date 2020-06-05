package com.tencent.bkrepo.composer.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 */
class ComposerArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val COMPOSER_DEPLOY = "/{projectId}/{repoName}/*"
        const val COMPOSER_JSON = "/{projectId}/{repoName}/**/*.json"
        const val COMPOSER_INSTALL = "/{projectId}/{repoName}/direct-dists/**"
        const val COMPOSER_PACKAGES = "/{projectId}/{repoName}/packages.json"
    }
}
