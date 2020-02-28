package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext

interface PypiRepository {
    fun searchXml(context: ArtifactSearchContext, xmlString: String)
}
