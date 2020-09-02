package com.tencent.bkrepo.common.artifact.config

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.composite.CompositeRepository
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository

/**
 * 依赖源配置类
 */
interface ArtifactConfiguration {
    /**
     * 获取依赖源类型[RepositoryType]
     */
    fun getRepositoryType(): RepositoryType = RepositoryType.NONE

    /**
     * 获取本地仓库实现类[LocalRepository]
     */
    fun getLocalRepository(): LocalRepository

    /**
     * 获取远程仓库实现类[RemoteRepository]
     */
    fun getRemoteRepository(): RemoteRepository

    /**
     * 获取虚拟仓库实现类[VirtualRepository]
     */
    fun getVirtualRepository(): VirtualRepository

    /**
     * 获取组合仓库实现类[CompositeRepository]
     */
    fun getCompositeRepository(): CompositeRepository
}
