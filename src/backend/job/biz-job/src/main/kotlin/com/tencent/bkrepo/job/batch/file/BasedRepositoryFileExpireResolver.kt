package com.tencent.bkrepo.job.batch.file

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.query.util.MongoEscapeUtils
import com.tencent.bkrepo.common.storage.filesystem.cleanup.FileExpireResolver
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.FULL_PATH
import com.tencent.bkrepo.job.LAST_ACCESS_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.SIZE
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils
import com.tencent.bkrepo.job.pojo.TFileCache
import com.tencent.bkrepo.job.service.FileCacheService
import com.tencent.bkrepo.repository.api.NodeClient
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.util.unit.DataSize
import java.io.File
import java.time.LocalDateTime

/**
 * 基于仓库配置判断文件是否过期
 * */
class BasedRepositoryFileExpireResolver(
    private val expireConfig: RepositoryExpireConfig,
    taskScheduler: ThreadPoolTaskScheduler,
    private val fileCacheService: FileCacheService,
    private val mongoTemplate: MongoTemplate,
) : FileExpireResolver {

    private var retainNodes = mutableSetOf<String>()

    init {
        taskScheduler.scheduleWithFixedDelay(this::refreshRetainNode, expireConfig.cacheTime)
    }

    override fun isExpired(file: File): Boolean {
        return !retainNodes.contains(file.name)
    }

    private fun refreshRetainNode() {
        logger.info("Refresh retain nodes start. size of nodes ${retainNodes.size}")
        try {
            val temp = mutableSetOf<String>()
            temp.addAll(getNodeFromConfig())
            temp.addAll(getNodeFromDataBase())
            retainNodes = temp
        } catch (e: Exception) {
            logger.warn("An error occurred while refreshing retain node $e")
        }
        logger.info("Refresh retain nodes finished. size of nodes ${retainNodes.size}")
    }
    
    private fun getNodeFromConfig(): Set<String> {
        val temp = mutableSetOf<String>()
        expireConfig.repos.map{ convertRepoConfigToFileCache(it) }.forEach {
            val projectId = it.projectId
            val repoName = it.repoName
            val records = getNodes(it)
            records.forEach { ret ->
                // 获取每个的sha256
                val sha256 = ret[SHA256].toString()
                val fullPath = ret[FULL_PATH].toString()
                temp.add(sha256)
                logger.info("Retain node $projectId/$repoName$fullPath, $sha256.")
            }
        }
        return temp
    }

    private fun getNodeFromDataBase(): Set<String> {
        val temp = mutableSetOf<String>()
        fileCacheService.list().forEach {
            val projectId = it.projectId
            val repoName = it.repoName
            val records = getNodes(it)
            records.forEach { ret ->
                // 获取每个的sha256
                val sha256 = ret[SHA256].toString()
                val fullPath = ret[FULL_PATH].toString()
                temp.add(sha256)
                logger.info("Retain node $projectId/$repoName$fullPath, $sha256.")
            }
        }
        return temp
    }

    private fun convertRepoConfigToFileCache(repoConfig: RepoConfig):TFileCache {
        return TFileCache(
            id = null,
            projectId = repoConfig.projectId,
            repoName = repoConfig.repoName,
            pathPrefix = repoConfig.pathPrefix,
            days = repoConfig.days,
            size = expireConfig.size.toMegabytes()
        )
    }

    private fun getNodes(tFileCache: TFileCache): Set<Map<String, Any?>> {
        val dateTime = LocalDateTime.now().minusDays(tFileCache.days.toLong())
        val collectionName = COLLECTION_NODE_PREFIX +
            MongoShardingUtils.shardingSequence(tFileCache.projectId, SHARDING_COUNT)
        return queryNodes(
            projectId = tFileCache.projectId,
            repoName = tFileCache.repoName,
            size = tFileCache.size,
            dateTime = dateTime,
            collection = collectionName,
            pathPrefixs = tFileCache.pathPrefix
        )
    }

    private fun queryNodes(
        projectId: String,
        repoName: String,
        size: Long,
        dateTime: LocalDateTime,
        collection: String,
        batchSize: Int = 20000,
        pathPrefixs: List<String>
    ): Set<Map<String, Any?>> {
        val temp = mutableSetOf<Map<String, Any?>>()
        val prefixCri = pathPrefixs.map {
            val escapedValue = MongoEscapeUtils.escapeRegex(it)
            Criteria.where(FULL_PATH).regex("^$escapedValue")
        }
        val query = Query.query(
            Criteria.where(PROJECT).isEqualTo(projectId).and(REPO).isEqualTo(repoName)
                .and(FOLDER).isEqualTo(false).and(SIZE).gte(DataSize.ofMegabytes(size).toBytes())
                .and(LAST_ACCESS_DATE).gt(dateTime).andOperator(Criteria().orOperator(prefixCri))
                .and(DELETED_DATE).isEqualTo(null)
        )

        val fields = query.fields()
        fields.include(SHA256, FULL_PATH)
        var querySize: Int
        var lastId = ObjectId(MIN_OBJECT_ID)
        do {
            val newQuery = Query.of(query)
                .addCriteria(Criteria.where(ID).gt(lastId))
                .limit(batchSize)
                .with(Sort.by(ID).ascending())
            val data = mongoTemplate.find<Map<String, Any?>>(
                newQuery,
                collection,
            )
            if (data.isEmpty()) {
                break
            }
            temp.addAll(data)
            querySize = data.size
            lastId = data.last()[ID] as ObjectId
        } while (querySize == batchSize)
        return temp
    }


    companion object {
        private val logger = LoggerFactory.getLogger(BasedRepositoryFileExpireResolver::class.java)
        private const val COLLECTION_NODE_PREFIX = "node_"

    }
}
