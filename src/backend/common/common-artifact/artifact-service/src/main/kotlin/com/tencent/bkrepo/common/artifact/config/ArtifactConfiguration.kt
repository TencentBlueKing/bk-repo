package com.tencent.bkrepo.common.artifact.config

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType

/**
 * 依赖源配置类
 */
interface ArtifactConfiguration {
    /**
     * 获取依赖源类型[RepositoryType]
     */
    fun getRepositoryType(): RepositoryType = RepositoryType.NONE
}
