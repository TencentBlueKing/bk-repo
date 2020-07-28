package com.tencent.bkrepo.common.artifact.api

/**
 *
 * @author: carrypan
 * @date: 2019/12/2
 */
class DefaultArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val DEFAULT_MAPPING_URI = "/{projectId}/{repoName}/**"
    }
}
