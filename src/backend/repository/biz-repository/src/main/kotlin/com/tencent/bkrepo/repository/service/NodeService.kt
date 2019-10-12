package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode.ELEMENT_CANNOT_BE_MODIFIED
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.ELEMENT_NOT_FOUND
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PARAMETER_IS_EXIST
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.model.TFileBlock
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.FileBlock
import com.tencent.bkrepo.repository.pojo.Node
import com.tencent.bkrepo.repository.pojo.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.NodeUpdateRequest
import com.tencent.bkrepo.repository.repository.FileBlockRepository
import com.tencent.bkrepo.repository.repository.NodeRepository
import com.tencent.bkrepo.repository.util.NodeUtils
import com.tencent.bkrepo.repository.util.NodeUtils.combineFullPath
import com.tencent.bkrepo.repository.util.NodeUtils.escapeRegex
import com.tencent.bkrepo.repository.util.NodeUtils.formatFullPath
import com.tencent.bkrepo.repository.util.NodeUtils.formatPath
import com.tencent.bkrepo.repository.util.NodeUtils.isRootDir
import com.tencent.bkrepo.repository.util.NodeUtils.parseDirName
import com.tencent.bkrepo.repository.util.NodeUtils.parseFileName
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
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
    private val fileBlockRepository: FileBlockRepository,
    private val mongoTemplate: MongoTemplate
) {
    fun getDetailById(id: String): Node? {
        val node = toNode(nodeRepository.findByIdAndDeletedIsNull(id)) ?: return null
        fileBlockRepository.findByNodeIdOrderBySequence(node.id).let { node.fileBlockList = it }

        return node
    }

    fun query(repositoryId: String, fullPath: String): Node? {
        val formattedPath = formatFullPath(fullPath)
        val tNode = mongoTemplate.findOne(Query(
                Criteria.where("repositoryId").`is`(repositoryId)
                        .and("fullPath").`is`(formattedPath)
                        .and("deleted").`is`(null)
        ), TNode::class.java)

        val node = toNode(tNode) ?: return null
        fileBlockRepository.findByNodeIdOrderBySequence(node.id).let { node.fileBlockList = it }

        return node
    }

    fun list(repositoryId: String, path: String): List<Node> {
        return nodeRepository.findByRepositoryIdAndPathAndDeletedIsNull(repositoryId, formatPath(path))
    }

    fun page(repositoryId: String, path: String, page: Int, size: Int): Page<Node> {
        val tNodePage = nodeRepository.findByRepositoryIdAndPathAndDeletedIsNull(repositoryId, formatPath(path), PageRequest.of(page, size))
        return Page(page, size, tNodePage.totalElements, tNodePage.content)
    }

    fun exist(repositoryId: String, fullPath: String): Boolean {
        val formattedPath = formatFullPath(fullPath)
        // 如果为根目录，直接判断仓库是否存在。仓库存在则根目录存在
        if (isRootDir(formattedPath)) {
            return mongoTemplate.findById(repositoryId, TRepository::class.java) != null
        }

        val query = Query(Criteria.where("repositoryId").`is`(repositoryId)
                .and("fullPath").`is`(formattedPath)
                .and("deleted").`is`(null))

        return mongoTemplate.exists(query, TNode::class.java)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun create(nodeCreateRequest: NodeCreateRequest): IdValue {
        val node = nodeCreateRequest.let {
            val path = parseDirName(it.path)
            val name = parseFileName(it.name)
            TNode(
                folder = it.folder,
                path = path,
                name = name,
                fullPath = combineFullPath(path, name),
                repositoryId = it.repositoryId,
                expires = if (it.folder) 0 else it.expires,
                size = if (it.folder) 0 else it.size ?: 0,
                sha256 = if (it.folder) null else it.sha256,
                createdBy = it.createdBy,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = it.createdBy,
                lastModifiedDate = LocalDateTime.now()
            )
        }

        // 路径唯一性校验
        val existNode = query(node.repositoryId, node.fullPath)
        if (existNode != null) {
            if (!nodeCreateRequest.overwrite) {
                throw ErrorCodeException(PARAMETER_IS_EXIST)
            } else if (existNode.folder || node.folder) {
                throw ErrorCodeException(ELEMENT_CANNOT_BE_MODIFIED)
            } else {
                // 存在相同路径文件且允许覆盖，删除之前的文件
                deleteById(existNode.id, node.lastModifiedBy, false)
            }
        }
        // 判断父目录是否存在，不存在先创建
        mkdirs(node.repositoryId, node.path, nodeCreateRequest.createdBy)
        // 保存节点
        val idValue = IdValue(nodeRepository.insert(node).id!!)
        // 保存分块信息
        nodeCreateRequest.blockList?.map {
            TFileBlock(
                    sequence = it.sequence,
                    size = it.size,
                    sha256 = it.sha256,
                    nodeId = idValue.id,
                    fullPath = node.fullPath,
                    repositoryId = node.repositoryId,
                    createdBy = nodeCreateRequest.createdBy,
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = nodeCreateRequest.createdBy,
                    lastModifiedDate = LocalDateTime.now()
            )
        }?.toList()?.run { fileBlockRepository.saveAll(this) }

        return idValue
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun updateById(id: String, nodeUpdateRequest: NodeUpdateRequest) {
        val node = nodeRepository.findByIdOrNull(id) ?: throw ErrorCodeException(ELEMENT_NOT_FOUND)
        // 文件夹不允许修改
        node.takeIf { !it.folder } ?: throw ErrorCodeException(ELEMENT_CANNOT_BE_MODIFIED)

        with(nodeUpdateRequest) {
            path?.let { node.path = parseDirName(it) }
            name?.let { node.name = parseFileName(it) }
            size?.let { node.size = it }
            sha256?.let { node.sha256 = it }
            expires?.let { node.expires }
            node.lastModifiedDate = LocalDateTime.now()
            node.lastModifiedBy = modifiedBy
        }

        val newFullPath = combineFullPath(node.path, node.name)
        // 如果path和name有变化，校验唯一性
        if (node.fullPath != newFullPath) {
            takeIf { !exist(node.repositoryId, newFullPath) } ?: throw ErrorCodeException(PARAMETER_IS_EXIST)
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
            mongoTemplate.updateMulti(query, update, Node::class.java)
            mongoTemplate.updateMulti(query, update, FileBlock::class.java)
        } else {
            // 硬删除，同时会删除block
            mongoTemplate.remove(query, Node::class.java)
            mongoTemplate.remove(query, FileBlock::class.java)
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
        mongoTemplate.remove(query, TFileBlock::class.java)
    }

    /**
     * 递归创建目录
     */
    private fun mkdirs(repositoryId: String, path: String, createdBy: String) {
        val parentPath = NodeUtils.getParentPath(path)
        val name = NodeUtils.getName(path)
        takeIf { !exist(repositoryId, parentPath) }?.run {
            mkdirs(repositoryId, parentPath, createdBy)
            nodeRepository.insert(TNode(
                    folder = true,
                    path = parentPath,
                    name = name,
                    fullPath = combineFullPath(parentPath, name),
                    repositoryId = repositoryId,
                    size = 0,
                    expires = 0,
                    createdBy = createdBy,
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = createdBy,
                    lastModifiedDate = LocalDateTime.now()
            ))
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeService::class.java)

        fun toNode(tNode: TNode?): Node? {
            return tNode?.let { Node(
                    it.id!!,
                    it.createdBy,
                    it.createdDate,
                    it.lastModifiedBy,
                    it.lastModifiedDate,
                    it.folder,
                    it.path,
                    it.name,
                    it.fullPath,
                    it.size,
                    it.sha256,
                    it.repositoryId
                )
            }
        }
    }
}
