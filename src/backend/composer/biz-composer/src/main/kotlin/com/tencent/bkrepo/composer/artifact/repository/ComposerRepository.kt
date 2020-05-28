package com.tencent.bkrepo.composer.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext

interface ComposerRepository {
    fun packages(context: ArtifactSearchContext): String?

    fun getJson(context: ArtifactSearchContext): String?
}
