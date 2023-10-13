package com.tencent.bkrepo.git.artifact

class GitPackFileArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String,
    private val fileName: String
) : GitRepositoryArtifactInfo(projectId, repoName, artifactUri) {

    companion object {
        const val PACKS = "packs"
    }

    override fun getArtifactFullPath(): String {
        return if(getArtifactMappingUri().isNullOrEmpty()) {
            "$PACKS/$fileName"
        } else getArtifactMappingUri()!!
    }
}
