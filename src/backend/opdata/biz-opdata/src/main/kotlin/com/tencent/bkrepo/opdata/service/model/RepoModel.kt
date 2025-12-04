package com.tencent.bkrepo.opdata.service.model

import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.opdata.constant.OPDATA_PROJECT_ID
import com.tencent.bkrepo.opdata.constant.OPDATA_REPOSITORY
import com.tencent.bkrepo.opdata.model.RepoInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class RepoModel @Autowired constructor(
    private var mongoTemplate: MongoTemplate
) {
    private val repoInfoCache = CacheBuilder.newBuilder()
        .maximumSize(DEFAULT_CACHE_SIZE)
        .expireAfterWrite(DEFAULT_CACHE_DURATION_SECONDS, TimeUnit.MINUTES)
        .build<String, RepoInfo>()

    fun getRepoListByProjectId(projectId: String): List<RepoInfo> {
        val query = Query(
            Criteria.where(OPDATA_PROJECT_ID).`is`(projectId)
        )
        return mongoTemplate.find(query, RepoInfo::class.java, OPDATA_REPOSITORY)
    }

    fun getRepoInfo(projectId: String, repoName: String): RepoInfo? {
        val cacheKey = "$projectId/$repoName"
        return repoInfoCache.getIfPresent(cacheKey) ?: run {
            val criteria = RepoInfo::projectId.isEqualTo(projectId)
                .and(RepoInfo::name.name).isEqualTo(repoName)
            mongoTemplate
                .findOne(Query(criteria), RepoInfo::class.java, OPDATA_REPOSITORY)
                ?.apply { repoInfoCache.put(cacheKey, this) }
        }
    }

    companion object {
        private const val DEFAULT_CACHE_SIZE = 1000L
        private const val DEFAULT_CACHE_DURATION_SECONDS = 5L
    }
}