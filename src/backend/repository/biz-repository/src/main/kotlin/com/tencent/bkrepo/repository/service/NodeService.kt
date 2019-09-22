package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode.ELEMENT_NOT_FOUND
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.Node
import com.tencent.bkrepo.repository.pojo.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.NodeUpdateRequest
import com.tencent.bkrepo.repository.repository.NodeRepository
import org.apache.commons.lang.StringUtils
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
import java.time.LocalDateTime

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
        // TODO: path末尾应该不含/，根目录为空
        return nodeRepository.findByRepositoryIdAndPath(repositoryId, path)
    }

    fun page(repositoryId: String, path: String, page: Int, size: Int): Page<Node> {
        // TODO: path末尾应该不含/，根目录为空
        val tNodePage = nodeRepository.findByRepositoryIdAndPath(repositoryId, path, PageRequest.of(page, size))
        return Page(page, size, tNodePage.totalElements, tNodePage.content)
    }


    @Transactional(rollbackFor = [Throwable::class])
    fun create(nodeCreateRequest: NodeCreateRequest): IdValue {
        // TODO: 校验数据格式  path name
        // TODO: 唯一性校验
        val tNode = nodeCreateRequest.let { TNode(
                folder = it.folder,
                path = it.path,
                name = it.name,
                size = it.size,
                sha256 = it.sha256,
                repositoryId = it.repositoryId
            )
        }
        tNode.fullPath = "${tNode.path}/${tNode.name}"
        return IdValue(nodeRepository.insert(tNode).id)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun updateById(id: String, nodeUpdateRequest: NodeUpdateRequest) {
        // TODO: 校验数据格式 path name
        // TODO: 唯一性校验
        // 检查是否为文件夹
        val node = nodeRepository.findByIdOrNull(id) ?: return
        if(!node.folder!!){
            // 文件夹直接返回，不允许修改
            return
        }
        val tNode = nodeUpdateRequest.let { TNode(
                id = id,
                path = it.path,
                name = it.name,
                size = it.size,
                sha256 = it.sha256
            )
        }
        tNode.fullPath = "${tNode.path}/${tNode.name}"
        nodeRepository.save(tNode)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun logicDeleteById(id: String) {
        // TODO: 逻辑删除，到期清理，还需要清理文件和元数据
        // TODO: 还需要判断是否为目录
        val node = nodeRepository.findByIdOrNull(id) ?: return
        node.deleted = LocalDateTime.now()
        nodeRepository.save(node)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun logicDeleteByPath(repositoryId: String, path: String) {
        // TODO: 逻辑删除，到期清理，还需要清理文件和元数据
        val subNodeQuery = Query(Criteria.where("repositoryId").`is`(repositoryId)
                .and("path").`is`(path)
                .and("folder").`is`(false))
        val nodeQuery = Query(Criteria.where("repositoryId").`is`(repositoryId)
                .and("fullPath").`is`(path)
                .and("folder").`is`(true))
        val update = Update().set("deleted", LocalDateTime.now())
                .set("lastModifiedDate", LocalDateTime.now())
                .set("lastModifiedBy", "")
        mongoTemplate.updateMulti(subNodeQuery, update, Node::class.java)
        mongoTemplate.updateMulti(nodeQuery, update, Node::class.java)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeService::class.java)

        fun toNode(tNode: TNode?): Node? {
            return tNode?.let { Node(
                    id = it.id!!,
                    folder = it.folder!!,
                    path = it.path!!,
                    name = it.name!!,
                    fullPath = it.fullPath!!,
                    size = it.size!!,
                    sha256 = it.sha256!!,
                    repositoryId = it.repositoryId!!
                )
            }
        }
    }
}