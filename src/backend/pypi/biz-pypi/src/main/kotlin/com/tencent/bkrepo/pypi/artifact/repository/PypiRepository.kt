package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext

/**
 * pypi 单独的接口
 */
interface PypiRepository {
    fun searchXml(context: ArtifactSearchContext, xmlString: String)
}
