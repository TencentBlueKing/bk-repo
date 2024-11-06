package com.tencent.bkrepo.git.internal

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.git.config.GitProperties
import com.tencent.bkrepo.git.constant.DEFAULT_BRANCH
import com.tencent.bkrepo.git.constant.HEAD
import com.tencent.bkrepo.git.internal.storage.CodeRepository
import com.tencent.bkrepo.git.internal.storage.CodeRepositoryBuilder
import com.tencent.bkrepo.git.internal.storage.DEFAULT_BLOCK_SIZE
import com.tencent.bkrepo.git.internal.storage.DEFAULT_STREAM_PACK_BUFFER_SIZE
import com.tencent.bkrepo.git.service.CodeRepositoryDataService
import org.eclipse.jgit.internal.storage.dfs.DfsReaderOptions
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import net.javacrumbs.shedlock.core.LockProvider

/**
 * code git仓库解析器
 * */
@Component
class CodeRepositoryResolver(
    gitProperties: GitProperties,
    codeRepositoryDataService: CodeRepositoryDataService,
    lockProvider: LockProvider
) {

    init {
        Companion.gitProperties = gitProperties
        Companion.lockProvider = lockProvider
        dataService = codeRepositoryDataService
    }

    companion object {
        private lateinit var gitProperties: GitProperties
        private lateinit var dataService: CodeRepositoryDataService
        private lateinit var lockProvider: LockProvider
        private val logger = LoggerFactory.getLogger(CodeRepositoryResolver::class.java)
        private val repositoryCache: LoadingCache<RepositoryKey, CodeRepository> by lazy {
            val cacheLoader = object : CacheLoader<RepositoryKey, CodeRepository>() {
                override fun load(key: RepositoryKey): CodeRepository {
                    return createRepository(key)
                }
            }
            CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .removalListener<RepositoryKey, CodeRepository> { it.value?.close() }
                .build(cacheLoader)
        }

        /**
         * 打开一个仓库，优先使用缓存，如果没有则构建
         * */
        fun open(projectId: String, repoName: String, storageCredentials: StorageCredentials?): CodeRepository {
            val repositoryKey = RepositoryKey(projectId, repoName, storageCredentials)
            return repositoryCache.get(repositoryKey)
        }

        private fun setDefaultBranchIfNeed(repository: CodeRepository) {
            repository.exactRef(HEAD) ?: let {
                repository.exactRef(DEFAULT_BRANCH)?.let {
                    repository.setDefaultBranch(DEFAULT_BRANCH)
                }
            }
        }

        private fun createRepository(repositoryKey: RepositoryKey): CodeRepository {
            with(repositoryKey) {
                logger.info("Create repository $repositoryKey")
                val readerOptions = DfsReaderOptions().setStreamPackBufferSize(DEFAULT_STREAM_PACK_BUFFER_SIZE)
                return CodeRepositoryBuilder(
                    projectId = projectId,
                    repoName = repoName,
                    storageCredentials = credentials,
                    dataService = dataService,
                    blockSize = DEFAULT_BLOCK_SIZE,
                    lockProvider = lockProvider
                ).setReaderOptions(readerOptions)
                    .setRepositoryDescription(DfsRepositoryDescription(this.toString()))
                    .build().apply {
                        setDefaultBranchIfNeed(this)
                    }
            }
        }

        /**
         * 仓库标识类
         */
        data class RepositoryKey(val projectId: String, val repoName: String, val credentials: StorageCredentials?) {
            override fun toString(): String {
                return StringBuilder(projectId)
                    .append(CharPool.SLASH)
                    .append(repoName)
                    .toString()
            }
        }
    }
}
