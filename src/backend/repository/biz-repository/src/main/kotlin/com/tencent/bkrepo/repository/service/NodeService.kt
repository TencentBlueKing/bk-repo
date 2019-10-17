package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode.ELEMENT_CANNOT_BE_MODIFIED
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PARAMETER_IS_EXIST
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.constant.RepositoryMessageCode
import com.tencent.bkrepo.repository.constant.RepositoryMessageCode.FOLDER_CANNOT_BE_MODIFIED
import com.tencent.bkrepo.repository.model.TFileBlock
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.FileBlock
import com.tencent.bkrepo.repository.pojo.node.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeSearchRequest
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeUpdateRequest
import com.tencent.bkrepo.repository.repository.NodeRepository
import com.tencent.bkrepo.repository.util.NodeUtils
import com.tencent.bkrepo.repository.util.NodeUtils.combineFullPath
import com.tencent.bkrepo.repository.util.NodeUtils.escapeRegex
import com.tencent.bkrepo.repository.util.NodeUtils.formatFullPath
import com.tencent.bkrepo.repository.util.NodeUtils.formatPath
import com.tencent.bkrepo.repository.util.NodeUtils.getName
import com.tencent.bkrepo.repository.util.NodeUtils.getParentPath
import com.tencent.bkrepo.repository.util.NodeUtils.isRootPath
import com.tencent.bkrepo.repository.util.NodeUtils.parseFullPath
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 节点service
 *
 * @author: carrypan
 * @date: 2019-09-20
 */
@Service
class NodeService @Autowired constructor(
    private val nodeRepository: NodeRepository,
    private val repositoryService: RepositoryService,
    private val mongoTemplate: MongoTemplate
) {
    fun queryDetail(projectId: String, repoName: String, fullPath: String, repoType: String? = null): NodeDetail? {
        checkRepository(projectId, repoName, repoType)
        val formattedFullPath = formatFullPath(fullPath)

        return convertToDetail(queryModel(projectId, repoName, formattedFullPath))
    }

    private fun queryModel(projectId: String, repoName: String, fullPath: String): TNode? {
        val query = createNodeQuery(projectId, repoName, fullPath, withDetail = true)

        return mongoTemplate.findOne(query, TNode::class.java)
    }

    fun getSize(projectId: String, repoName: String, fullPath: String): NodeSizeInfo {
        checkRepository(projectId, repoName)

        val formattedFullPath = formatFullPath(fullPath)
        val node = queryModel(projectId, repoName, formattedFullPath)
                ?: throw ErrorCodeException(RepositoryMessageCode.NODE_NOT_FOUND, formattedFullPath)
        // 节点为文件直接返回
        if (!node.folder) {
            return NodeSizeInfo(size = node.size)
        }
        val escapedPath = escapeRegex(formatPath(formattedFullPath))

        val criteria = Criteria.where("projectId").`is`(projectId)
                .and("repoName").`is`(repoName)
                .and("fullPath").regex("^$escapedPath")
                .and("deleted").`is`(null)

        val count = mongoTemplate.count(Query(criteria), TNode::class.java)

        val aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group().sum("size").`as`("size")
        )
        val aggregateResult = mongoTemplate.aggregate(aggregation, TNode::class.java, HashMap::class.java)
        val size = aggregateResult.mappedResults.takeIf { it.size > 0 }?.run {
            this[0].getOrDefault("size", 0) as Long
        } ?: 0
        return NodeSizeInfo(subNodeCount = count, size = size)
    }

    fun list(projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): List<NodeInfo> {
        checkRepository(projectId, repoName)
        val query = createListQuery(projectId, repoName, path, includeFolder, deep)

        return mongoTemplate.find(query, TNode::class.java).map { convert(it)!! }
    }

    fun page(projectId: String, repoName: String, path: String, page: Int, size: Int, includeFolder: Boolean, deep: Boolean): Page<NodeInfo> {
        checkRepository(projectId, repoName)
        val query = createListQuery(projectId, repoName, path, includeFolder, deep).with(PageRequest.of(page, size))

        val listData = mongoTemplate.find(query, TNode::class.java).map { convert(it)!! }
        val count = mongoTemplate.count(query, TNode::class.java)

        return Page(page, size, count, listData)
    }

    fun search(nodeSearchRequest: NodeSearchRequest): List<NodeInfo> {
        val projectId = nodeSearchRequest.projectId
        val repoName = nodeSearchRequest.repoName
        checkRepository(projectId, repoName)

        val query = createNodeQuery(projectId, repoName)
        // 路径匹配
        val criteria = Criteria()
        val criteriaList = nodeSearchRequest.pathPattern.map {
            val escapedPath = escapeRegex(formatPath(it))
            Criteria.where("fullPath").regex("^$escapedPath")
        }
        criteria.orOperator(*criteriaList.toTypedArray())
        // 元数据匹配
        nodeSearchRequest.metadataCondition.filterKeys { it.isNotBlank() }.forEach { (key, value) -> criteria.and("metadata.$key").`is`(value) }
        query.addCriteria(criteria)

        return mongoTemplate.find(query, TNode::class.java).map { convert(it)!! }
    }

    fun exist(projectId: String, repoName: String, fullPath: String): Boolean {
        val formattedPath = formatFullPath(fullPath)
        val query = createNodeQuery(projectId, repoName, formattedPath)

        return mongoTemplate.exists(query, TNode::class.java)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun create(nodeCreateRequest: NodeCreateRequest): IdValue {
        val projectId = nodeCreateRequest.projectId
        val repoName = nodeCreateRequest.repoName
        val fullPath = parseFullPath(nodeCreateRequest.fullPath)

        checkRepository(projectId, repoName)
        // 路径唯一性校验
        val existNode = queryModel(projectId, repoName, fullPath)
        if (existNode != null) {
            if (!nodeCreateRequest.overwrite) throw ErrorCodeException(PARAMETER_IS_EXIST, fullPath)
            else if (existNode.folder || nodeCreateRequest.folder) throw ErrorCodeException(FOLDER_CANNOT_BE_MODIFIED)
            else {
                // 存在相同路径文件节点且允许覆盖，删除之前的节点
                deleteByPath(projectId, repoName, fullPath, nodeCreateRequest.operator)
            }
        }
        // 判断父目录是否存在，不存在先创建
        mkdirs(projectId, repoName, getParentPath(fullPath), nodeCreateRequest.operator)
        // 创建节点
        val node = nodeCreateRequest.let {
            TNode(
                folder = it.folder,
                path = getParentPath(fullPath),
                name = getName(fullPath),
                fullPath = fullPath,
                expireDate = if (it.folder) null else parseExpireDate(it.expires),
                size = if (it.folder) 0 else it.size ?: 0,
                sha256 = if (it.folder) null else it.sha256,
                metadata = it.metadata ?: emptyMap(),
                projectId = projectId,
                repoName = repoName,
                createdBy = it.operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = it.operator,
                lastModifiedDate = LocalDateTime.now()
            )
        }
        node.blockList = nodeCreateRequest.blockList?.map { TFileBlock(sequence = it.sequence, sha256 = it.sha256, size = it.size) }
        // 保存节点
        val idValue = IdValue(nodeRepository.insert(node).id!!)

        logger.info("Create node [$nodeCreateRequest] success.")
        return idValue
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun update(nodeUpdateRequest: NodeUpdateRequest) {
        val projectId = nodeUpdateRequest.projectId
        val repoName = nodeUpdateRequest.repoName
        val operator = nodeUpdateRequest.operator
        val formattedFullPath = formatFullPath(nodeUpdateRequest.fullPath)

        checkRepository(projectId, repoName)
        val node = queryModel(projectId, repoName, formattedFullPath) ?: throw ErrorCodeException(RepositoryMessageCode.NODE_NOT_FOUND, formattedFullPath)
        // 文件夹不允许修改
        if (node.folder) {
            throw ErrorCodeException(ELEMENT_CANNOT_BE_MODIFIED)
        }

        nodeUpdateRequest.newFullPath?.let {
            val newFullPath = parseFullPath(it)
            // 如果path和name有变化，校验唯一性
            if (node.fullPath != newFullPath) {
                takeIf { !exist(projectId, repoName, newFullPath) } ?: throw ErrorCodeException(PARAMETER_IS_EXIST, newFullPath)
                node.path = getParentPath(newFullPath)
                node.name = getName(newFullPath)
                node.fullPath = newFullPath
                // 判断父目录是否存在，不存在先创建
                mkdirs(projectId, repoName, node.path, operator)
            }
        }
        nodeUpdateRequest.expires?.let { node.expireDate = parseExpireDate(it) }
        node.lastModifiedDate = LocalDateTime.now()
        node.lastModifiedBy = operator

        nodeRepository.save(node)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun copy(nodeCopyRequest: NodeCopyRequest) {
        // TODO:
        // val node = queryNodeDetail(nodeCopyRequest.fromRepoId, nodeCopyRequest)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun delete(nodeDeleteRequest: NodeDeleteRequest) {
        with(nodeDeleteRequest) {
            checkRepository(this.projectId, this.repoName)
            deleteByPath(this.projectId, this.repoName, this.fullPath, this.operator)
        }
    }

    /**
     * 根据全路径删除文件或者目录
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun deleteByPath(projectId: String, repoName: String, fullPath: String, operator: String, soft: Boolean = true) {
        checkRepository(projectId, repoName)
        val formattedPath = formatFullPath(fullPath)
        val escapedPath = escapeRegex(formattedPath)
        val query = createNodeQuery(projectId, repoName)
        query.addCriteria(Criteria().orOperator(
                Criteria.where("fullPath").regex("^$escapedPath/"),
                Criteria.where("fullPath").`is`(formattedPath)
        ))
        if (soft) {
            // 软删除
            val update = Update.update("deleted", LocalDateTime.now())
                    .set("lastModifiedDate", LocalDateTime.now())
                    .set("lastModifiedBy", operator)
            mongoTemplate.updateMulti(query, update, TNode::class.java)
        } else {
            // 硬删除
            mongoTemplate.remove(query, TNode::class.java)
        }
        logger.info("Delete node [$projectId/$repoName/$fullPath] by [$operator] success.")
    }

    /**
     * 递归创建目录
     */
    private fun mkdirs(projectId: String, repoName: String, path: String, createdBy: String) {
        if (!exist(projectId, repoName, path)) {
            val parentPath = getParentPath(path)
            val name = getName(path)

            if (!isRootPath(path)) {
                mkdirs(projectId, repoName, parentPath, createdBy)
            }
            nodeRepository.insert(TNode(
                    folder = true,
                    path = parentPath,
                    name = name,
                    fullPath = combineFullPath(parentPath, name),
                    size = 0,
                    expireDate = null,
                    metadata = emptyMap(),
                    projectId = projectId,
                    repoName = repoName,
                    createdBy = createdBy,
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = createdBy,
                    lastModifiedDate = LocalDateTime.now()
            ))
        }
    }

    fun checkRepository(projectId: String, repoName: String, repoType: String? = null) {
        if (!repositoryService.exist(projectId, repoName, repoType)) {
            throw ErrorCodeException(RepositoryMessageCode.REPOSITORY_NOT_FOUND, repoName)
        }
    }

    private fun createNodeQuery(projectId: String, repoName: String, fullPath: String? = null, withDetail: Boolean = false): Query {
        val criteria = Criteria.where("projectId").`is`(projectId)
                .and("repoName").`is`(repoName)
                .and("deleted").`is`(null)

        val query = Query(criteria)

        fullPath?.run { criteria.and("fullPath").`is`(fullPath) }
        takeUnless { withDetail }.run { query.fields().exclude("metadata").exclude("blockList") }

        return query
    }

    private fun createListQuery(projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): Query {
        val formattedPath = formatPath(path)
        val escapedPath = escapeRegex(formattedPath)
        val query = createNodeQuery(projectId, repoName)
        if (deep) {
            query.addCriteria(Criteria.where("fullPath").regex("^$escapedPath"))
        } else {
            query.addCriteria(Criteria.where("path").`is`(formattedPath))
        }
        if (!includeFolder) {
            query.addCriteria(Criteria.where("folder").`is`(false))
        }
        return query
    }

    private fun parseExpireDate(expireDays: Long?): LocalDateTime? {
        return expireDays?.let {
            if (it > 0) LocalDateTime.now().plusDays(it) else null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeService::class.java)

        private fun convert(tNode: TNode?): NodeInfo? {
            return tNode?.let {
                NodeInfo(
                    id = it.id!!,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate,
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate,
                    folder = it.folder,
                    path = it.path,
                    name = it.name,
                    fullPath = it.fullPath,
                    size = it.size,
                    sha256 = it.sha256,
                    repoName = it.repoName,
                    projectId = it.projectId
                )
            }
        }

        private fun convertToDetail(tNode: TNode?): NodeDetail? {
            return tNode?.let {
                NodeDetail(
                    nodeInfo = convert(it)!!,
                    metadata = it.metadata ?: emptyMap(),
                    blockList = it.blockList?.map { item -> convert(item) }
                )
            }
        }

        private fun convert(tFileBlock: TFileBlock): FileBlock {
            return tFileBlock.let { FileBlock(sequence = it.sequence, sha256 = it.sha256, size = it.size) }
        }
    }
}
