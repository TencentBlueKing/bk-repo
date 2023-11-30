package com.tencent.bkrepo.job.batch.file

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.storage.filesystem.cleanup.FileExpireResolver
import com.tencent.bkrepo.job.FULL_PATH
import com.tencent.bkrepo.job.LAST_ACCESS_DATE
import com.tencent.bkrepo.job.SHA256
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import java.io.File
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * 基于仓库配置判断文件是否过期
 * */
class BasedRepositoryFileExpireResolver(
    private val nodeClient: NodeClient,
    private val expireConfig: RepositoryExpireConfig,
    taskScheduler: ThreadPoolTaskScheduler,
) : FileExpireResolver {

    private var retainNodes = mutableSetOf<String>()

    init {
        taskScheduler.scheduleWithFixedDelay(this::refreshRetainNode, expireConfig.cacheTime)
    }

    override fun isExpired(file: File): Boolean {
        return !retainNodes.contains(file.name)
    }

    private fun refreshRetainNode() {
        logger.info("Refresh retain nodes.")
        val newRetainNodes = mutableSetOf<String>()
        expireConfig.repos.forEach {
            // for each repo
            val projectId = it.projectId
            val repoName = it.repoName

            val queryModel = NodeQueryBuilder()
                .projectId(it.projectId)
                .repoName(it.repoName)
                .excludeFolder()
                .size(expireConfig.size.toBytes(), OperationType.GT)
                .rule(LAST_ACCESS_DATE, LocalDateTime.now().minusDays(it.days.toLong()), OperationType.AFTER)
                .page(1, expireConfig.max)
                .select(SHA256, FULL_PATH).build()
            val fullPathRuleList = it.pathPrefix.map { prefix ->
                Rule.QueryRule(FULL_PATH, prefix, OperationType.PREFIX)
            }
            val rule = Rule.NestedRule(fullPathRuleList.toMutableList(), Rule.NestedRule.RelationType.OR)
            (queryModel.rule as Rule.NestedRule).rules.add(rule)
            val pages = nodeClient.queryWithoutCount(queryModel)
            pages.data?.records?.forEach { ret ->
                // 获取每个的sha256
                val sha256 = ret[SHA256].toString()
                val fullPath = ret[FULL_PATH].toString()
                newRetainNodes.add(sha256)
                logger.info("Retain node $projectId/$repoName$fullPath, $sha256.")
            }
        }
        retainNodes = newRetainNodes
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BasedRepositoryFileExpireResolver::class.java)
    }
}
