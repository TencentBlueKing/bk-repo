package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode.ELEMENT_CANNOT_BE_MODIFIED
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.ELEMENT_NOT_FOUND
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PARAMETER_IS_EXIST
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.model.TFileBlock
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.FileBlock
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeSearchRequest
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeUpdateRequest
import com.tencent.bkrepo.repository.repository.NodeRepository
import com.tencent.bkrepo.repository.util.NodeUtils
import com.tencent.bkrepo.repository.util.NodeUtils.ROOT_PATH
import com.tencent.bkrepo.repository.util.NodeUtils.combineFullPath
import com.tencent.bkrepo.repository.util.NodeUtils.escapeRegex
import com.tencent.bkrepo.repository.util.NodeUtils.formatFullPath
import com.tencent.bkrepo.repository.util.NodeUtils.formatPath
import com.tencent.bkrepo.repository.util.NodeUtils.parseFileName
import com.tencent.bkrepo.repository.util.NodeUtils.parsePathName
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.repository.findByIdOrNull
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
    private val mongoTemplate: MongoTemplate
) {
    fun queryNodeDetail(repositoryId: String, fullPath: String): NodeDetail? {
        val formattedPath = formatFullPath(fullPath)
        val tNode = mongoTemplate.findOne(Query(
                Criteria.where("repositoryId").`is`(repositoryId)
                        .and("fullPath").`is`(formattedPath)
                        .and("deleted").`is`(null)
        ), TNode::class.java)

        return convertToDetail(tNode) ?: return null
    }

    fun queryNodeInfo(repositoryId: String, fullPath: String): NodeInfo? {
        val formattedPath = formatFullPath(fullPath)
        val tNode = mongoTemplate.findOne(createNodeQuery().addCriteria(
                Criteria.where("repositoryId").`is`(repositoryId)
                        .and("fullPath").`is`(formattedPath)
                        .and("deleted").`is`(null)
        ), TNode::class.java)

        return convert(tNode) ?: return null
    }

    fun getNodeSize(repositoryId: String, fullPath: String): NodeSizeInfo {
        val formattedPath = formatPath(fullPath)
        val escapedPath = escapeRegex(formattedPath)

        val criteria = Criteria.where("repositoryId").`is`(repositoryId)
                .and("fullPath").regex("^$escapedPath")
                .and("deleted").`is`(null)

        val count = mongoTemplate.count(Query(criteria), TNode::class.java)

        val aggregation = Aggregation.newAggregation(
                Aggregation.project("repositoryId", "size", "folder", "fullPath", "deleted"),
                Aggregation.match(criteria),
                Aggregation.group("repositoryId").sum("size").`as`("size")
        )
        val aggregateResult = mongoTemplate.aggregate(aggregation, TNode::class.java, HashMap::class.java)
        val size = aggregateResult.mappedResults.takeIf { it.size > 0 }?.run {
            this[0].getOrDefault("size", 0) as Long
        } ?: 0
        return NodeSizeInfo(subNodeCount = count, size = size)
    }

    fun list(repositoryId: String, path: String, includeFolder: Boolean, deep: Boolean): List<NodeInfo> {
        val query = createListQuery(repositoryId, path, includeFolder, deep)

        return mongoTemplate.find(query, TNode::class.java).map { convert(it)!! }
    }

    fun page(repositoryId: String, path: String, page: Int, size: Int, includeFolder: Boolean, deep: Boolean): Page<NodeInfo> {
        val query = createListQuery(repositoryId, path, includeFolder, deep).with(PageRequest.of(page, size))

        val listData = mongoTemplate.find(query, TNode::class.java).map { convert(it)!! }
        val count = mongoTemplate.count(query, TNode::class.java)

        return Page(page, size, count, listData)
    }

    fun search(repositoryId: String, nodeSearchRequest: NodeSearchRequest): List<NodeInfo> {
        // TODO: 实现搜索
        return emptyList()
    }

    fun exist(repositoryId: String, fullPath: String): Boolean {
        val formattedPath = formatFullPath(fullPath)
        val query = Query(Criteria.where("repositoryId").`is`(repositoryId)
                .and("fullPath").`is`(formattedPath)
                .and("deleted").`is`(null))

        return mongoTemplate.exists(query, TNode::class.java)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun createRootPath(repositoryId: String, createdBy: String) {
        if (!exist(repositoryId, ROOT_PATH)) {
            val node = TNode(
                folder = true,
                path = ROOT_PATH,
                name = "",
                fullPath = ROOT_PATH,
                repositoryId = repositoryId,
                size = 0,
                createdBy = createdBy,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = createdBy,
                lastModifiedDate = LocalDateTime.now()
            )
            nodeRepository.insert(node)
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun create(nodeCreateRequest: NodeCreateRequest): IdValue {
        val path = parsePathName(nodeCreateRequest.path)
        val name = parseFileName(nodeCreateRequest.name)
        val fullPath = combineFullPath(path, name)
        // 路径唯一性校验
        val existNode = queryNodeInfo(nodeCreateRequest.repositoryId, fullPath)
        if (existNode != null) {
            if (!nodeCreateRequest.overwrite) {
                throw ErrorCodeException(PARAMETER_IS_EXIST)
            } else if (existNode.folder || nodeCreateRequest.folder) {
                throw ErrorCodeException(ELEMENT_CANNOT_BE_MODIFIED)
            } else {
                // 存在相同路径文件节点且允许覆盖，删除之前的节点
                deleteById(existNode.id, nodeCreateRequest.createdBy, false)
            }
        }
        // 判断父目录是否存在，不存在先创建
        mkdirs(nodeCreateRequest.repositoryId, nodeCreateRequest.path, nodeCreateRequest.createdBy)
        // 创建节点
        val node = nodeCreateRequest.let {
            TNode(
                folder = it.folder,
                path = path,
                name = name,
                fullPath = fullPath,
                repositoryId = it.repositoryId,
                expireDate = if (it.folder) null else parseExpireDate(it.expires),
                size = if (it.folder) 0 else it.size ?: 0,
                sha256 = if (it.folder) null else it.sha256,
                metadata = it.metadata ?: emptyMap(),
                createdBy = it.createdBy,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = it.createdBy,
                lastModifiedDate = LocalDateTime.now()
            )
        }
        node.blockList = nodeCreateRequest.blockList?.map { TFileBlock(sequence = it.sequence, sha256 = it.sha256, size = it.size) }
        // 保存节点
        return IdValue(nodeRepository.insert(node).id!!)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun updateById(id: String, nodeUpdateRequest: NodeUpdateRequest) {
        val node = nodeRepository.findByIdOrNull(id) ?: throw ErrorCodeException(ELEMENT_NOT_FOUND)
        // 文件夹不允许修改
        node.takeIf { !it.folder } ?: throw ErrorCodeException(ELEMENT_CANNOT_BE_MODIFIED)

        with(nodeUpdateRequest) {
            path?.let { node.path = parsePathName(it) }
            name?.let { node.name = parseFileName(it) }
            expires?.let { node.expireDate = parseExpireDate(expires) }
            node.lastModifiedDate = LocalDateTime.now()
            node.lastModifiedBy = modifiedBy
        }

        val newFullPath = combineFullPath(node.path, node.name)
        // 如果path和name有变化，校验唯一性
        if (node.fullPath != newFullPath) {
            takeIf { !exist(node.repositoryId, newFullPath) } ?: throw ErrorCodeException(PARAMETER_IS_EXIST, newFullPath)
            node.fullPath = newFullPath
            // 判断父目录是否存在，不存在先创建
            mkdirs(node.repositoryId, node.path, nodeUpdateRequest.modifiedBy)
        }

        nodeRepository.save(node)
    }

    /**
     * 删除文件，定期清理
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun deleteById(id: String, modifiedBy: String, soft: Boolean = true) {
        val node = nodeRepository.findByIdOrNull(id) ?: return
        deleteByPath(node.repositoryId, node.fullPath, modifiedBy, soft)
    }

    /**
     * 根据全路径删除文件或者目录
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun deleteByPath(repositoryId: String, fullPath: String, modifiedBy: String, soft: Boolean = true) {
        val formattedPath = formatFullPath(fullPath)
        val escapedPath = escapeRegex(formattedPath)
        val query = Query(Criteria.where("repositoryId").`is`(repositoryId)
                .orOperator(Criteria.where("fullPath").regex("^$escapedPath/"), Criteria.where("fullPath").`is`(formattedPath))
                .and("deleted").`is`(null))
        if (soft) {
            // 软删除
            val update = Update.update("deleted", LocalDateTime.now())
                    .set("lastModifiedDate", LocalDateTime.now())
                    .set("lastModifiedBy", modifiedBy)
            mongoTemplate.updateMulti(query, update, TNode::class.java)
        } else {
            // 硬删除
            mongoTemplate.remove(query, TNode::class.java)
        }
    }

    /**
     * 删除某个仓库下所有文件
     * 因为仓库已经删除，因此永久删除文件，不做保留
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun deleteByRepositoryId(repoId: String) {
        val query = Query(Criteria.where("repositoryId").`is`(repoId))
        mongoTemplate.remove(query, TNode::class.java)
    }

    /**
     * 递归创建目录
     */
    private fun mkdirs(repositoryId: String, path: String, createdBy: String) {
        if (!exist(repositoryId, path)) {
            val parentPath = NodeUtils.getParentPath(path)
            val name = NodeUtils.getName(path)
            mkdirs(repositoryId, parentPath, createdBy)
            nodeRepository.insert(TNode(
                    folder = true,
                    path = parentPath,
                    name = name,
                    fullPath = combineFullPath(parentPath, name),
                    repositoryId = repositoryId,
                    size = 0,
                    createdBy = createdBy,
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = createdBy,
                    lastModifiedDate = LocalDateTime.now()
            ))
        }
    }

    private fun createNodeQuery(): Query {
        val query = Query().with(Sort.by("name"))
        query.fields().exclude("metadata").exclude("blockList")
        return query
    }

    private fun createListQuery(repositoryId: String, path: String, includeFolder: Boolean, deep: Boolean): Query {
        val formattedPath = formatPath(path)
        val escapedPath = escapeRegex(formattedPath)
        val criteria = Criteria.where("repositoryId").`is`(repositoryId).and("deleted").`is`(null)
        if (deep) {
            criteria.and("fullPath").regex("^$escapedPath")
        } else {
            criteria.and("path").`is`(formattedPath)
        }
        if (!includeFolder) {
            criteria.and("folder").`is`(false)
        }
        return createNodeQuery().addCriteria(criteria)
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
                    repositoryId = it.repositoryId
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
