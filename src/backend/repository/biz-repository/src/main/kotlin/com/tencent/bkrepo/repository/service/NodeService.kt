package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode.ELEMENT_CANNOT_BE_MODIFIED
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.ELEMENT_NOT_FOUND
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PARAMETER_IS_EXIST
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.Node
import com.tencent.bkrepo.repository.pojo.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.NodeUpdateRequest
import com.tencent.bkrepo.repository.repository.NodeRepository
import com.tencent.bkrepo.repository.util.NodeUtils
import com.tencent.bkrepo.repository.util.NodeUtils.combineFullPath
import com.tencent.bkrepo.repository.util.NodeUtils.parseDirName
import com.tencent.bkrepo.repository.util.NodeUtils.parseFileName
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 仓库service
 *
 * @author: carrypan
 * @date: 2019-09-20
 */
@Service
class NodeService @Autowired constructor(
    private val nodeRepository: NodeRepository,
    private val mongoTemplate: MongoTemplate
) {
    fun getDetailById(id: String): Node {
        return toNode(nodeRepository.findByIdOrNull(id)) ?: throw ErrorCodeException(ELEMENT_NOT_FOUND)
    }

    fun list(repositoryId: String, path: String): List<Node> {
        return nodeRepository.findByRepositoryIdAndPathAndDeletedIsNull(repositoryId, path)
    }

    fun page(repositoryId: String, path: String, page: Int, size: Int): Page<Node> {
        val tNodePage = nodeRepository.findByRepositoryIdAndPathAndDeletedIsNull(repositoryId, path, PageRequest.of(page, size))
        return Page(page, size, tNodePage.totalElements, tNodePage.content)
    }

    fun exist(repositoryId: String, fullPath: String): Boolean {
        fullPath.takeIf { it.isNotBlank() } ?: return true
        val query = Query(Criteria.where("repositoryId").`is`(repositoryId)
                .and("fullPath").`is`(fullPath)
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
                size = if (it.folder) 0 else it.size ?: 0,
                sha256 = if (it.folder) null else it.sha256,
                createdBy = it.createdBy,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = it.createdBy,
                lastModifiedDate = LocalDateTime.now()
            )
        }
        // 路径唯一性校验
        node.takeIf { !exist(it.repositoryId, it.fullPath) } ?: throw ErrorCodeException(PARAMETER_IS_EXIST)
        // 判断父目录是否存在，不存在先创建
        mkdirs(node.repositoryId, node.path, nodeCreateRequest.createdBy)

        return IdValue(nodeRepository.insert(node).id!!)
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
            node.lastModifiedDate = LocalDateTime.now()
            node.lastModifiedBy = modifiedBy
        }
        val newFullPath = combineFullPath(node.path, node.name)
        // 如果path和name有变化，校验格式和唯一性
        if (node.fullPath != newFullPath) {
            takeIf { !exist(node.repositoryId, newFullPath) } ?: throw ErrorCodeException(PARAMETER_IS_EXIST)
            node.fullPath = newFullPath
            // 判断父目录是否存在，不存在先创建
            mkdirs(node.repositoryId, node.path, nodeUpdateRequest.modifiedBy)
        }
        nodeRepository.save(node)
    }

    /**
     * 软删除文件，定期清理
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun softDeleteById(id: String, modifiedBy: String) {
        val node = nodeRepository.findByIdOrNull(id) ?: throw ErrorCodeException(ELEMENT_NOT_FOUND)
        if (node.folder && page(node.repositoryId, node.fullPath, 0, 10).count > 0) {
            throw ErrorCodeException(ELEMENT_CANNOT_BE_MODIFIED, "文件夹包含子文件，无法删除！")
        }
        node.apply {
            deleted = LocalDateTime.now()
            lastModifiedDate = LocalDateTime.now()
            lastModifiedBy = modifiedBy
        }
        nodeRepository.save(node)
    }

    /**
     * 删除某个仓库下所有文件
     * 因为仓库已经删除，因此永久删除文件，不做保留
     */
    fun deleteByRepoId(repoId: String) {
        mongoTemplate.remove(Query(Criteria.where("repositoryId").`is`(repoId)), TNode::class.java)
    }

    /**
     * 递归创建目录
     */
    private fun mkdirs(repositoryId: String, path: String, createdBy: String) {
        val parentPath = NodeUtils.getParentPath(path)
        takeIf { !exist(repositoryId, parentPath) }?.run {
            mkdirs(repositoryId, parentPath, createdBy)
            nodeRepository.insert(TNode(
                    folder = true,
                    path = parentPath,
                    name = NodeUtils.getName(path),
                    fullPath = path,
                    repositoryId = repositoryId,
                    size = 0,
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
