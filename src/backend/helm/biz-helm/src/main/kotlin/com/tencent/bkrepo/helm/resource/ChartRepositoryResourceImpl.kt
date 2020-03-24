package com.tencent.bkrepo.helm.resource

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.helm.api.ChartRepositoryResource

class ChartRepositoryResourceImpl : ChartRepositoryResource {
    override fun getIndexYaml() {
        val context = ArtifactDownloadContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.download(context)
    }
}