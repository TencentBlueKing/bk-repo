package com.tencent.bkrepo.repository.dao.repository

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.repository.model.TProxyChannel
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * 代理源mongo repository
 */
@Repository
interface ProxyChannelRepository : MongoRepository<TProxyChannel, String> {
    fun findByNameAndRepoType(name: String, repoType: RepositoryType): TProxyChannel?
    fun findByUrlAndRepoType(url: String, repoType: RepositoryType): TProxyChannel?
    fun findByPublicAndRepoType(public: Boolean, repoType: String): List<TProxyChannel>
}
