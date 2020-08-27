package com.tencent.bkrepo.common.artifact.api

class DefaultArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val DEFAULT_MAPPING_URI = "/{projectId}/{repoName}/**"
    }
}
