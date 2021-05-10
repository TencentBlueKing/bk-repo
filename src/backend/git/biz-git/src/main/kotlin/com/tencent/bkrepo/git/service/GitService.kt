package com.tencent.bkrepo.git.service

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.git.artifact.GitContentArtifactInfo
import com.tencent.bkrepo.git.artifact.GitRepositoryArtifactInfo
import com.tencent.bkrepo.git.artifact.repository.GitRemoteRepository
import com.tencent.bkrepo.common.redis.RedisLock
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.git.constant.REDIS_SET_REPO_TO_UPDATE
import com.tencent.bkrepo.git.constant.convertorLockKey
import com.tencent.bkrepo.common.redis.RedisOperation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Service
class GitService(
    private val redisOperation: RedisOperation
) : ArtifactService() {
    val executor: ThreadPoolExecutor = ThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors() * 2,
        200, 60, TimeUnit.SECONDS, LinkedBlockingQueue(10000)
    )

    private val logger = LoggerFactory.getLogger(GitService::class.java)

    companion object {
        private const val expiredTimeInSeconds: Long = 120L
    }

    fun sync(infoRepository: GitRepositoryArtifactInfo) {
        val context = ArtifactDownloadContext()
        val task = {
            val name = context.artifactInfo.getArtifactName()
            val key = convertorLockKey(name)
            val lock = RedisLock(redisOperation, key, expiredTimeInSeconds)
            if (lock.tryLock()) {
                lock.use {
                    val repository = ArtifactContextHolder.getRepository(RepositoryCategory.REMOTE)
                    (repository as GitRemoteRepository).sync(context)
                }
            } else {
                logger.debug("not acquire lock $key")
                redisOperation.addSetValue(REDIS_SET_REPO_TO_UPDATE, name)
                logger.debug("add $REDIS_SET_REPO_TO_UPDATE $name")
            }
        }
        executor.submit(task)
        context.response.contentType = MediaTypes.APPLICATION_JSON
        context.response.writer.println(ResponseBuilder.success().toJsonString())
    }

    fun getContent(gitContentArtifactInfo: GitContentArtifactInfo) {
        repository.download(ArtifactDownloadContext())
    }
}
