package com.tencent.bkrepo.common.artifact.config

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType

/**
 * 依赖源配置类
 */
interface ArtifactConfiguration {
    fun getRepositoryType(): RepositoryType = RepositoryType.NONE
}
