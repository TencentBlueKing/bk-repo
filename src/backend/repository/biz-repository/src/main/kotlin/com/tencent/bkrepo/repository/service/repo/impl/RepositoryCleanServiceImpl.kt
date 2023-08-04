package com.tencent.bkrepo.repository.service.repo.impl

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.constant.StringPool.ROOT
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.event.repo.RepositoryCleanEvent
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.clean.CleanStatus
import com.tencent.bkrepo.common.artifact.pojo.configuration.clean.RepositoryCleanStrategy
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.dao.RepositoryDao
import com.tencent.bkrepo.repository.job.clean.CleanRepoTaskScheduler
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TPackageVersion
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.node.NodeDelete
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import com.tencent.bkrepo.repository.service.node.NodeDeleteOperation
import com.tencent.bkrepo.repository.service.node.NodeStatsOperation
import com.tencent.bkrepo.repository.service.packages.PackageService
import com.tencent.bkrepo.repository.service.repo.RepositoryCleanService
import com.tencent.bkrepo.repository.service.repo.RepositoryService
import com.tencent.bkrepo.repository.util.RepoCleanRuleUtils
import com.tencent.bkrepo.repository.util.RuleUtils
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.util.stream.Collectors

@Service
class RepositoryCleanServiceImpl(
    private val repositoryDao: RepositoryDao,
    private val repositoryService: RepositoryService,
    private val taskScheduler: CleanRepoTaskScheduler,
    private val packageService: PackageService,
    private val nodeDeleteOperation: NodeDeleteOperation,
    private val nodeStatsOperation: NodeStatsOperation,
    private val nodeClient: NodeClient,
    private val publisher: ApplicationEventPublisher
) : RepositoryCleanService {

    override fun cleanRepo(repoId: String) {
        val tRepository = repositoryDao.findById(repoId)
        tRepository?.let { repo ->
            val cleanStrategy = repositoryService.getRepoCleanStrategy(repo.projectId, repo.name)
            cleanStrategy?.let {
                logger.info(
                    "projectId:[${repo.projectId}] repoName:[${repo.name}] " +
                        "clean strategy autoClean:[${it.autoClean}] status:[${it.status}]"
                )
                // 自动清理关闭，状态为 WAITING，删除job
                if (!it.autoClean && it.status == CleanStatus.WAITING) {
                    taskScheduler.deleteJob(repoId)
                    return
                }
                execute(repo, it)
            } ?: logger.warn("projectId:[${repo.projectId}] repoName:[${repo.name}] clean strategy is null")
        } ?: logger.error("argument exception tRepository is null, tRepository:[$tRepository]")
    }

    override fun cleanRepoDebug(projectId: String, repoName: String) {
        val tRepository = repositoryDao.findByNameAndType(projectId, repoName)
        tRepository?.let { repo ->
            val cleanStrategy = repositoryService.getRepoCleanStrategy(repo.projectId, repo.name)
            cleanStrategy?.let {
                logger.info(
                    "projectId:[${repo.projectId}] repoName:[${repo.name}] " +
                        "clean strategy autoClean:[${it.autoClean}] status:[${it.status}]"
                )
                execute(repo, it)
            } ?: logger.warn("projectId:[${repo.projectId}] repoName:[${repo.name}] clean strategy is null")
        } ?: logger.error("argument exception tRepository is null, tRepository:[$tRepository]")
    }

    private fun execute(
        repo: TRepository,
        cleanStrategy: RepositoryCleanStrategy
    ) {
        try {
            repositoryService.updateCleanStatus(repo.projectId, repo.name, true)
            if (repo.type == RepositoryType.GENERIC) {
                // 将rule 转为 Map<Regex, Rule>
                val flattenRule = RepoCleanRuleUtils.flattenRule(cleanStrategy)
                flattenRule?.let {
                    if (logger.isDebugEnabled) {
                        logger.debug("flattenRule:[${flattenRule.toJsonString()}]")
                    }
                    executeNodeCleanV2(repo.projectId, repo.name, ROOT, it)
                }
            } else {
                executeClean(repo.projectId, repo.name, cleanStrategy)
            }
            repositoryService.updateCleanStatus(repo.projectId, repo.name, false)
        } catch (ex: IllegalArgumentException) {
            logger.error("repo clean fail exception:[$ex]")
        } catch (ex: Exception) {
            repositoryService.updateCleanStatus(repo.projectId, repo.name, false)
            logger.error("projectId:[${repo.projectId}] repoName:[${repo.name}] clean error [$ex]")
        }
    }

    /**
     * 依赖源清理：执行规则过滤，并删除
     */
    private fun executeClean(
        projectId: String,
        repoName: String,
        cleanStrategy: RepositoryCleanStrategy
    ) {
        var packageList = packageService.listPackagePage(projectId, repoName, DEFAULT_PAGE_SIZE, null)
        var lastPackageKey: String
        while (packageList.isNotEmpty()) {
            // 记录上一页最后一条记录的 key
            lastPackageKey = packageList.last().key
            executePackageClean(packageList, cleanStrategy)
            packageList = packageService.listPackagePage(projectId, repoName, DEFAULT_PAGE_SIZE, lastPackageKey)
        }
    }

    private fun executePackageClean(
        packageList: List<PackageSummary>,
        cleanStrategy: RepositoryCleanStrategy
    ) {
        packageList.forEach {
            val deleteVersions: MutableList<PackageVersion>
            var ruleQueryList = mutableListOf<PackageVersion>()
            requireNotNull(it.id)
            // 包的版本数 <= 保留版本数，直接跳过
            if (it.versions <= cleanStrategy.reserveVersions) return@forEach
            val listVersion = packageService.listAllVersion(
                it.projectId,
                it.repoName,
                it.key,
                VersionListOption()
            ).toMutableList()
            cleanStrategy.rule?.let { rule ->
                ruleQueryList = metadataRuleQuery(rule, it.id!!).toMutableList()
            }
            if (logger.isDebugEnabled) {
                logger.debug(
                    "projectId:[${it.projectId}] repoName:[${it.repoName}] " +
                        "packageName:[${it.name}] rule query result:[$ruleQueryList]"
                )
            }
            listVersion.removeAll(ruleQueryList)
            // 根据保留规则筛选后，需要删除的版本数小于保留版本数
            if (listVersion.size < cleanStrategy.reserveVersions) return@forEach
            deleteVersions = reserveVersionsAndDaysFilter(
                listVersion,
                cleanStrategy.reserveVersions,
                cleanStrategy.reserveDays
            ).toMutableList()
            if (logger.isDebugEnabled) {
                logger.debug(
                    "projectId:[${it.projectId}] repoName:[${it.repoName}] clean [packageName:${it.name}] " +
                        "delete version collection: $deleteVersions"
                )
            }
            deleteVersion(deleteVersions, it.key, it.type, it.projectId, it.repoName)
        }
    }

    /**
     * 元数据规则查询
     */
    private fun metadataRuleQuery(
        rule: Rule,
        packageId: String
    ): List<PackageVersion> {
        val versionList = mutableListOf<PackageVersion>()
        if (rule is Rule.NestedRule && rule.rules.isEmpty()) {
            return versionList
        }
        var pageNumber = 1
        val packageIdRule = Rule.QueryRule(TPackageVersion::packageId.name, packageId)
        val queryRule = Rule.NestedRule(mutableListOf(packageIdRule, rule))
        val queryModel = QueryModel(
            page = PageLimit(pageNumber, DEFAULT_PAGE_SIZE),
            sort = null,
            select = null,
            rule = queryRule
        )
        var versionPage = packageService.searchVersion(queryModel)
        while (versionPage.records.isNotEmpty()) {
            versionList.addAll(versionPage.records)
            pageNumber += 1
            queryModel.page = PageLimit(pageNumber, DEFAULT_PAGE_SIZE)
            versionPage = packageService.searchVersion(queryModel)
        }
        return versionList
    }

    /**
     * 执行 Generic 仓库清理
     */
    private fun executeNodeClean(
        projectId: String,
        repoName: String,
        cleanStrategy: RepositoryCleanStrategy
    ) {
        val projectIdRule = Rule.QueryRule(NodeInfo::projectId.name, projectId)
        val repoNameRule = Rule.QueryRule(NodeInfo::repoName.name, repoName)
        val allNodeQueryRule = Rule.NestedRule(mutableListOf(projectIdRule, repoNameRule))
        val allNodeList = nodeRuleQuery(allNodeQueryRule)
        var reserveNodeList = mutableListOf<NodeDelete>()
        var deleteNodeList: MutableList<NodeDelete>
        with(cleanStrategy) {
            rule?.let {
                reserveNodeList = nodeRuleQuery(it)
            }
            if (logger.isDebugEnabled) {
                logger.debug("project:[$projectId] repoName[$repoName] reverseNodeList:[$reserveNodeList]")
            }
            // 取【所有节点集合】 与 【保留规则集合】 的差集
            allNodeList.removeAll(reserveNodeList)
            // 保留天数过滤
            deleteNodeList = nodeReserveDaysFilter(allNodeList, reserveDays)
        }
        if (logger.isDebugEnabled) {
            logger.debug("projectId:[$projectId] repoName:[$repoName] delete list [$deleteNodeList]")
        }
        deleteNodeList.forEach {
            // 判断文件夹下是否有文件
            if (it.folder) {
                val countFileNode =
                    nodeStatsOperation.countFileNode(ArtifactInfo(it.projectId, it.repoName, it.fullPath))
                if (countFileNode > 0) return@forEach
            }
            nodeDeleteOperation.deleteByPath(it.projectId, it.repoName, it.fullPath, SYSTEM_USER)
        }
    }

    private fun executeNodeCleanV2(
        projectId: String,
        repoName: String,
        path: String,
        flatten: Map<String, Rule.NestedRule>,
        pageNumber: Int = 1
    ) {
        val nodelist = nodeClient.listNodePage(
            projectId = projectId,
            repoName = repoName,
            path = path,
            option = NodeListOption(
                pageNumber = pageNumber,
                pageSize = cleanPageSize,
                includeMetadata = true,
                includeFolder = true,
                sortProperty = listOf(TNode::id.name),
                direction = listOf(Sort.Direction.ASC.name)
            )
        ).data?.records
        if (nodelist != null) {
            logger.info("executeNodeCleanV2: [$projectId/$repoName$path], node size: ${nodelist.size}")
            nodelist.forEach {
                if (it.folder) {
                    executeNodeCleanV2(it.projectId, it.repoName, it.fullPath, flatten)
                } else {
                    if (!RepoCleanRuleUtils.needReserveWrapper(it, flatten)) {
                        try {
                            logger.info("executeNodeCleanV2: will delete node: " +
                                "[${it.projectId}/${it.repoName}/${it.fullPath}]")
                            nodeDeleteOperation.deleteByPath(it.projectId, it.repoName, it.fullPath, SYSTEM_USER)
                        } catch (e: Exception) {
                            logger.error("executeNodeCleanV2: delete node failed, node: $it", e)
                        }
                    }
                }
            }
            if (nodelist.size >= cleanPageSize) {
                executeNodeCleanV2(projectId, repoName, path, flatten, pageNumber + 1)
            }
        } else {
            logger.warn("list node page is null")
        }
    }

    /**
     * generic仓库清理：根据规则查询节点
     * @return MutableList<TNode> 节点集合
     */
    private fun nodeRuleQuery(rule: Rule): MutableList<NodeDelete> {
        val result = mutableListOf<NodeDelete>()
        if (rule is Rule.NestedRule && rule.rules.isEmpty()) return result
        val newRule = RuleUtils.rulePathToRegex(rule)
        var pageNumber = 1
        val queryModel = QueryModel(
            page = PageLimit(pageNumber, DEFAULT_PAGE_SIZE),
            sort = null,
            select = null,
            rule = newRule
        )
        var nodePage = nodeClient.search(queryModel).data
        nodePage?.let {
            while (nodePage!!.records.isNotEmpty()) {
                nodePage!!.records.map {
                    val projectId = it[TNode::projectId.name] as String
                    val repoName = it[TNode::repoName.name] as String
                    val fullPath = it[TNode::fullPath.name] as String
                    val folder = it[TNode::folder.name] as Boolean
                    val createdDate = LocalDateTime.parse(it[TNode::createdDate.name].toString())
                    val recentlyUseDate = it[TNode::recentlyUseDate.name]?.let { date ->
                        LocalDateTime.parse(date.toString())
                    }
                    val node = NodeDelete(projectId, repoName, folder, fullPath, createdDate, recentlyUseDate)
                    result.add(node)
                }
                pageNumber += 1
                queryModel.page = PageLimit(pageNumber, DEFAULT_PAGE_SIZE)
                nodePage = nodeClient.search(queryModel).data
            }
        }
        return result
    }

    /**
     * generic仓库清理：根据保留天数过滤节点
     * @return MutableList<TNode> ,返回大于【保留天数】的节点集合
     */
    private fun nodeReserveDaysFilter(nodeList: List<NodeDelete>, reserveDays: Long): MutableList<NodeDelete> {
        val deleteNodeList: MutableList<NodeDelete> = mutableListOf()
        val nowDate = LocalDateTime.now()
        nodeList.forEach {
            var useDays = Long.MAX_VALUE
            if (it.recentlyUseDate != null) {
                useDays = Duration.between(it.recentlyUseDate, nowDate).toDays()
            }
            val createdDays = Duration.between(it.createdDate, nowDate).toDays()
            if (createdDays >= reserveDays && useDays >= reserveDays) {
                deleteNodeList.add(it)
            }
        }
        return deleteNodeList
    }

    /**
     * 依赖源清理：保留版本数，保留天数过滤
     */
    private fun reserveVersionsAndDaysFilter(
        versions: List<PackageVersion>,
        reserveVersions: Long,
        reserveDays: Long
    ): List<PackageVersion> {
        val filterVersions: MutableList<PackageVersion> = mutableListOf()
        // 根据 【版本上传时间】 降序排序
        val sortedByDesc = versions.sortedByDescending { it.createdDate }
        // 截取超过【保留版本数】的版本，进行保留天数过滤
        val reserveDaysFilter =
            sortedByDesc.subList(reserveVersions.toInt(), versions.size).sortedByDescending { it.recentlyUseDate }
        if (logger.isDebugEnabled) {
            logger.debug("reverse version number filter result:[$reserveDaysFilter]")
        }
        val nowDate = LocalDateTime.now()
        reserveDaysFilter.forEach {
            var useDays = Long.MAX_VALUE
            if (it.recentlyUseDate != null) {
                useDays = Duration.between(it.recentlyUseDate, nowDate).toDays()
            }
            val createdDays = Duration.between(it.createdDate, nowDate).toDays()
            if (createdDays >= reserveDays && useDays >= reserveDays) {
                filterVersions.add(it)
            }
        }
        return filterVersions
    }

    /**
     * 删除对应依赖源的包版本
     */
    private fun deleteVersion(
        versions: List<PackageVersion>,
        packageKey: String,
        type: PackageType,
        projectId: String,
        repoName: String
    ) {
        val versionList = versions.stream().map(PackageVersion::name).collect(Collectors.toList())
        publisher.publishEvent(
            RepositoryCleanEvent(
                projectId,
                repoName,
                SYSTEM_USER,
                packageKey,
                versionList,
                type.toString(),
                null
            )
        )
    }

    companion object {
        private const val cleanPageSize = 1024 * 10
        private val logger = LoggerFactory.getLogger(RepositoryCleanServiceImpl::class.java)
    }
}
