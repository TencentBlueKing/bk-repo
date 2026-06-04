package com.tencent.bkrepo.common.metadata.util

import com.mongodb.ReadPreference
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.TooManyRequestsException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties.Companion.DELETE_MODE_BATCH_BY_IDS
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties.Companion.DELETE_MODE_UPDATE_WITH_HINT
import com.tencent.bkrepo.common.metadata.constant.ID
import com.tencent.bkrepo.common.metadata.model.TNode
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

object NodeDeleteHelper {

    /**
     * 执行节点删除（软删除），兼容同步与协程调用方。
     *
     * 根据 [deleteMode] 选择策略：
     * - update：直接 updateMulti，不使用 hint
     * - updateWithHint：updateMulti 附带 hint 强制走 FULL_PATH_IDX（需要 MongoDB 4.2+）
     * - batchByIds：分批从 Primary 通过 find（带 hint）查询节点 ID，再按 ID 批量 update
     *   兼容 MongoDB 4.2 以下版本
     *
     * [concurrency] 限制同时执行的删除操作数量，小于等于 0 表示不限制，超过上限时抛出[TooManyRequestsException]
     *
     * [maxDeleteNodeCount] 限制单次删除操作允许影响的最大节点数，小于等于 0 表示不限制。
     * 删除前会通过 [countByQuery] 执行 count 查询，超过上限时直接拒绝
     *
     * 利用 inline 展开使得 suspend 调用方可以在 lambda 中直接调用 suspend DAO 方法
     */
    inline fun deleteNodes(
        query: Query,
        deleteMode: String,
        batchSize: Int,
        concurrency: Int,
        maxDeleteNodeCount: Long = 0,
        operator: String,
        deleteTime: LocalDateTime,
        findByQuery: (Query) -> List<Map<*, *>>,
        updateMulti: (Query, Update) -> Long,
        countByQuery: (Query) -> Long = { 0L },
        useFullPathIndex: Boolean = true,
    ): Long {
        val running = runningDeleteCount.incrementAndGet()
        logger.info(
            "Delete nodes by [$operator], mode [$deleteMode], useFullPathIndex[$useFullPathIndex]"
        )
        try {
            checkConcurrencyLimit(running, concurrency)
            checkDeleteNodeCountLimit(query, maxDeleteNodeCount, useFullPathIndex, countByQuery)
            val deleteQuery = Query.of(query)
            return when {
                useFullPathIndex && deleteMode == DELETE_MODE_UPDATE_WITH_HINT -> {
                    deleteQuery.withHint(TNode.FULL_PATH_IDX)
                    updateMulti(deleteQuery, NodeQueryHelper.nodeDeleteUpdate(operator, deleteTime))
                }
                useFullPathIndex && deleteMode == DELETE_MODE_BATCH_BY_IDS -> {
                    deleteBatchByNodeIds(deleteQuery, batchSize, operator, deleteTime, findByQuery, updateMulti)
                }
                else -> updateMulti(deleteQuery, NodeQueryHelper.nodeDeleteUpdate(operator, deleteTime))
            }
        } finally {
            runningDeleteCount.decrementAndGet()
        }
    }

    /**
     * 校验并发执行名额。[concurrency] 小于等于 0 表示不限制。
     *
     * @param running 当前在途删除操作数（含本次）
     * @throws TooManyRequestsException 当前并发数已达上限
     */
    @PublishedApi
    internal fun checkConcurrencyLimit(running: Int, concurrency: Int) {
        if (concurrency > 0 && running > concurrency) {
            throw TooManyRequestsException("Concurrent deleteNodes operations exceed limit [$concurrency]")
        }
    }

    /**
     * 校验待删除节点数量是否超过上限。[maxDeleteNodeCount] 小于等于 0 表示不限制。
     *
     * 通过 [Query.of] 拷贝原始 query 避免 hint 影响后续 update；当 [useFullPathIndex] 为 true 时为 count 查询指定 hint
     *
     * @throws ErrorCodeException 待删除节点数超过上限
     */
    @PublishedApi
    internal inline fun checkDeleteNodeCountLimit(
        query: Query,
        maxDeleteNodeCount: Long,
        useFullPathIndex: Boolean,
        countByQuery: (Query) -> Long,
    ) {
        if (maxDeleteNodeCount <= 0) return
        val countQuery = Query.of(query)
        if (useFullPathIndex) countQuery.withHint(TNode.FULL_PATH_IDX)
        val count = countByQuery(countQuery)
        if (count > maxDeleteNodeCount) {
            throw ErrorCodeException(
                CommonMessageCode.PARAMETER_INVALID,
                "Delete node count [$count] exceeds limit [$maxDeleteNodeCount]"
            )
        }
    }

    @PublishedApi
    internal inline fun deleteBatchByNodeIds(
        query: Query,
        batchSize: Int,
        operator: String,
        deleteTime: LocalDateTime,
        findByQuery: (Query) -> List<Map<*, *>>,
        updateMulti: (Query, Update) -> Long,
    ): Long {
        // 限制删除范围为删除发起时刻（deleteTime）之前已存在的节点，避免删除过程中新建的节点被持续卷入导致循环无法收敛
        query.addCriteria(Criteria.where(TNode::createdDate.name).lte(deleteTime))
        query.withHint(TNode.FULL_PATH_IDX)
        query.fields().include(ID)
        query.limit(batchSize)
        query.withReadPreference(ReadPreference.primary())
        val update = NodeQueryHelper.nodeDeleteUpdate(operator, deleteTime)
        var totalModified = 0L
        var round = 0

        var docs = findByQuery(query)
        while (docs.isNotEmpty()) {
            val nodeIds = docs.map { it[ID]!! }
            val modifiedCount = updateMulti(Query(Criteria.where(ID).`in`(nodeIds)), update)
            if (modifiedCount != nodeIds.size.toLong()) {
                logger.error(
                    "Node batch delete modified count [$modifiedCount] mismatch with node id count " +
                        "[${nodeIds.size}], nodeIds [$nodeIds]"
                )
            }
            totalModified += modifiedCount
            round++
            // 每隔固定批次输出一次进度日志，便于监控大目录删除的进展
            if (round % LOG_ROUND_INTERVAL == 0) {
                logger.info(
                    "Node batch delete in progress, operator [$operator], deleteTime [$deleteTime], " +
                        "round [$round], deleted [$totalModified] nodes"
                )
            }
            docs = findByQuery(query)
        }

        return totalModified
    }

    fun buildCriteria(
        projectId: String,
        repoName: String,
        fullPath: String,
    ): Criteria {
        val normalizedFullPath = PathUtils.normalizeFullPath(fullPath)
        val escapedFullPath = PathUtils.escapeRegex(normalizedFullPath)
        // { projectId:"x", repoName:"y", deleted:null, $or: [ {fullPath:/^prefix/}, {fullPath:"exact"} ] }
        // $or 存在时，优化器对每个子句单独评估索引，两个子句都只涉及 fullPath，不涉及 path，理论上应该走 FULL_PATH_IDX。但实际走了 PATH_IDX，原因是：
        // MongoDB 处理顶层 $or 时，会用 index intersection 或 subplan 策略，
        // 如果某个子计划的 winning plan 是 PATH_IDX（例如之前有过一次 path 字段查询把 PATH_IDX 的缓存评分刷高了），
        // 优化器会复用缓存计划，导致错选。这是 MongoDB plan cache 污染问题，不是索引定义问题。
        // 单条 regex 同时匹配节点本身和所有子节点，消灭 $or 避免优化器选错索引：
        // ^/p/b$    匹配节点本身
        // ^/p/b/.*  匹配子节点
        // 根目录 "/" 需特殊处理：escapeRegex("/") = "\/"，生成的 regex "^\/(\/.*)?$" 只能匹配 "/" 和 "//..."
        // 而实际子节点路径形如 "/a/1"，不以 "//" 开头，因此必须用 "^\/.*" 匹配所有路径
        val regex = if (PathUtils.isRoot(normalizedFullPath)) {
            "^\\/.*"
        } else {
            "^$escapedFullPath(/.*)?$"
        }
        return where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::deleted).isEqualTo(null)
            .and(TNode::fullPath).regex(regex)
    }

    /**
     * 精确路径匹配，适用于已知是文件节点（非目录）的删除场景，避免正则前缀查询
     */
    fun buildFileCriteria(projectId: String, repoName: String, fullPath: String): Criteria {
        val normalizedFullPath = PathUtils.normalizeFullPath(fullPath)
        return where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::deleted).isEqualTo(null)
            .and(TNode::fullPath).isEqualTo(normalizedFullPath)
    }

    @PublishedApi
    internal val logger = LoggerFactory.getLogger(NodeDeleteHelper::class.java)

    /**
     * 当前正在执行的 deleteNodes 操作数量，用于并发限制与指标统计
     */
    @PublishedApi
    internal val runningDeleteCount = AtomicInteger(0)

    /**
     * 分批删除每隔该批次数输出一次进度日志
     */
    @PublishedApi
    internal const val LOG_ROUND_INTERVAL = 100
}
