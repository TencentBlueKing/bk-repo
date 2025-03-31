package com.tencent.bkrepo.job.batch.file

import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.FULL_PATH
import com.tencent.bkrepo.job.LAST_ACCESS_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.SIZE
import com.tencent.bkrepo.job.batch.base.BaseService
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils
import com.tencent.bkrepo.job.pojo.TFileCache
import com.tencent.bkrepo.job.service.FileCacheService
import java.time.LocalDateTime
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.util.unit.DataSize

/**
 * 基于仓库配置判断文件是否过期
 * */
class BasedRepositoryNodeRetainResolver(
    private val expireConfig: RepositoryExpireConfig,
    taskScheduler: ThreadPoolTaskScheduler,
    private val fileCacheService: FileCacheService,
    private val mongoTemplate: MongoTemplate,
    private val redisTemplate: RedisTemplate<String, String>,
    private val lockOperation: LockOperation
) : NodeRetainResolver, BaseService(redisTemplate, lockOperation) {

    private var retainNodes = HashMap<String, RetainNode>()

    init {
        taskScheduler.scheduleWithFixedDelay(this::refreshRetainNode, expireConfig.cacheTime)
    }

    override fun retain(sha256: String): Boolean {
        return getValue(sha256, retainNodes) != null
    }

    override fun getRetainNode(sha256: String): RetainNode? {
        return getValue(sha256, retainNodes)
    }

    private fun refreshRetainNode() {
        logger.info("Refresh retain nodes start. size of nodes ${retainNodes.size}")
        refreshData(RETAIN_NODES_REFRESH_JOB) {
            refreshRetainNodes()
        }
        logger.info("Refresh retain nodes finished. size of nodes ${retainNodes.size}")
    }

    private fun refreshRetainNodes() {
        try {
            val temp = HashMap<String, RetainNode>()
            val configs = expireConfig.repos.map { convertRepoConfigToFileCache(it) } + fileCacheService.list()
            configs.forEach { config ->
                getNodes(config).forEach { node ->
                    val retainNode = RetainNode(
                        projectId = config.projectId,
                        repoName = config.repoName,
                        fullPath = node[FULL_PATH].toString(),
                        sha256 = node[SHA256].toString(),
                        size = node[SIZE].toString().toLong()
                    )
                    temp[retainNode.sha256] = retainNode
                    logger.info("Retain node[$retainNode]")
                }
            }
            retainNodes = temp
            storeValue(retainNodes)
        } catch (e: Exception) {
            logger.warn("An error occurred while refreshing retain node $e")
        }
    }

    private fun storeValue(retainValue: HashMap<String, RetainNode>) {
        try {
            retainValue.forEach { (sha256, retainedNode) ->
                val redisKey = buildRedisKey(JOB_RETAIN_PREFIX, sha256)
                redisTemplate.opsForValue().set(redisKey, retainedNode.toJsonString())
            }
            // 避免内存中存储的活跃数据不是最新的
            retainValue.clear()
        } catch (e: Exception) {
            logger.warn("store retain nodes error: ${e.message}")
        }
    }

    private fun getValue(sha256: String, cacheValue: HashMap<String, RetainNode>): RetainNode? {
        if (cacheValue.isNotEmpty()) return cacheValue[sha256]
        try {
            val redisKey = buildRedisKey(JOB_RETAIN_PREFIX, sha256)
            val valueStr = redisTemplate.opsForValue().get(redisKey)
            if (valueStr.isNullOrEmpty()) {
                return null
            }
            return valueStr.readJsonString<RetainNode>()
        } catch (e: Exception) {
            logger.warn("get retain nodes from redis error:${e.message}")
            return null
        }
    }

    private fun convertRepoConfigToFileCache(repoConfig: RepoConfig): TFileCache {
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
            val escapedValue = EscapeUtils.escapeRegex(it)
            Criteria.where(FULL_PATH).regex("^$escapedValue")
        }
        val query = Query.query(
            Criteria.where(PROJECT).isEqualTo(projectId).and(REPO).isEqualTo(repoName)
                .and(FOLDER).isEqualTo(false).and(SIZE).gte(DataSize.ofMegabytes(size).toBytes())
                .and(LAST_ACCESS_DATE).gt(dateTime).andOperator(Criteria().orOperator(prefixCri))
                .and(DELETED_DATE).isEqualTo(null)
        )

        val fields = query.fields()
        fields.include(SHA256, FULL_PATH, SIZE)
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
        private val logger = LoggerFactory.getLogger(BasedRepositoryNodeRetainResolver::class.java)
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val RETAIN_NODES_REFRESH_JOB = "retainNodesRefreshJob"
        private const val JOB_RETAIN_PREFIX = "job:retainNodes:"

    }
}
