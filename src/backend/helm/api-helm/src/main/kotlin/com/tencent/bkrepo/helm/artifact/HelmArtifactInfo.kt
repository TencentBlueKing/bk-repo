package com.tencent.bkrepo.helm.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

class HelmArtifactInfo(
    projectId: String,
    repoName: String,
    artifactUri: String
) : ArtifactInfo(projectId, repoName, artifactUri) {
    companion object {
        const val CHARTS_LIST = "/{projectId}/{repoName}/api/charts/**"

        // helm upload
        const val HELM_PUSH_URL = "/api/{projectId}/{repoName}/charts"

        // chart delete
        const val CHART_DELETE_URL = "/{projectId}/{repoName}/api/charts/*/*"

        // get index.yaml
        const val HELM_INDEX_YAML_URL = "/{projectId}/{repoName}/index.yaml"
        // chart install
        const val HELM_INSTALL_URL = "/{projectId}/{repoName}/charts/*.tgz"
    }
}
