package com.tencent.bkrepo.helm.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.helm.pojo.ChartInfoList
import org.springframework.stereotype.Component

@Component
class HelmLocalRepository : LocalRepository(){
    override fun search(context: ArtifactSearchContext): ChartInfoList? {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        with(artifactInfo) {

        }
        return null
    }
}
