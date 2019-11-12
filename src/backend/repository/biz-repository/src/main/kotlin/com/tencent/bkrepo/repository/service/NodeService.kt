package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode.NOT_SUPPORTED
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PARAMETER_INVALID
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PARAMETER_IS_EXIST
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.constant.RepositoryMessageCode
import com.tencent.bkrepo.repository.constant.RepositoryMessageCode.FOLDER_CANNOT_BE_MODIFIED
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TFileBlock
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.node.FileBlock
import com.tencent.bkrepo.repository.pojo.node.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeOperateRequest
import com.tencent.bkrepo.repository.pojo.node.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.NodeSearchRequest
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.service.QueryHelper.nodeDeleteUpdate
import com.tencent.bkrepo.repository.service.QueryHelper.nodeListCriteria
import com.tencent.bkrepo.repository.service.QueryHelper.nodeListQuery
import com.tencent.bkrepo.repository.service.QueryHelper.nodePageQuery
import com.tencent.bkrepo.repository.service.QueryHelper.nodePathUpdate
import com.tencent.bkrepo.repository.service.QueryHelper.nodeQuery
import com.tencent.bkrepo.repository.service.QueryHelper.nodeRepoUpdate
import com.tencent.bkrepo.repository.service.QueryHelper.nodeSearchQuery
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
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
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
    private val repositoryService: RepositoryService,
    private val nodeDao: NodeDao
) {

    /**
     * 查询节点详情
     */
    fun queryDetail(projectId: String, repoName: String, fullPath: String, repoType: String? = null): NodeDetail? {
        repositoryService.checkRepository(projectId, repoName, repoType)
        val formattedFullPath = formatFullPath(fullPath)

        return convertToDetail(queryModel(projectId, repoName, formattedFullPath))
    }

    /**
     * 计算文件或者文件夹大小
     */
    fun getSize(projectId: String, repoName: String, fullPath: String): NodeSizeInfo {
        repositoryService.checkRepository(projectId, repoName)

        val formattedFullPath = formatFullPath(fullPath)
        val node = queryModel(projectId, repoName, formattedFullPath)
                ?: throw ErrorCodeException(RepositoryMessageCode.NODE_NOT_FOUND, formattedFullPath)
        // 节点为文件直接返回
        if (!node.folder) {
            return NodeSizeInfo(subNodeCount = 0, size = node.size)
        }

        val criteria = nodeListCriteria(projectId, repoName, formatPath(formattedFullPath), includeFolder = true, deep = true)
        val count = nodeDao.count(Query(criteria))

        val aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group().sum("size").`as`("size")
        )
        val aggregateResult = nodeDao.aggregate(aggregation, HashMap::class.java)
        val size = aggregateResult.mappedResults.takeIf { it.size > 0 }?.run {
            this[0].getOrDefault("size", 0) as Long
        } ?: 0
        return NodeSizeInfo(subNodeCount = count, size = size)
    }

    /**
     * 列表查询节点
     */
    fun list(projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): List<NodeInfo> {
        repositoryService.checkRepository(projectId, repoName)
        val query = nodeListQuery(projectId, repoName, path, includeFolder, deep)

        return nodeDao.find(query).map { convert(it)!! }
    }

    /**
     * 分页查询节点
     */
    fun page(projectId: String, repoName: String, path: String, page: Int, size: Int, includeFolder: Boolean, deep: Boolean): Page<NodeInfo> {
        page.takeIf { it >= 0 } ?: throw ErrorCodeException(PARAMETER_INVALID, "page")
        size.takeIf { it >= 0 } ?: throw ErrorCodeException(PARAMETER_INVALID, "size")
        repositoryService.checkRepository(projectId, repoName)

        val query = nodePageQuery(projectId, repoName, path, includeFolder, deep, page, size)

        val listData = nodeDao.find(query).map { convert(it)!! }
        val count = nodeDao.count(query)

        return Page(page, size, count, listData)
    }

    /**
     * 搜索节点
     */
    fun search(searchRequest: NodeSearchRequest): Page<NodeInfo> {
        searchRequest.page.takeIf { it >= 0 } ?: throw ErrorCodeException(PARAMETER_INVALID, "page")
        searchRequest.size.takeIf { it >= 0 } ?: throw ErrorCodeException(PARAMETER_INVALID, "size")
        searchRequest.repoNameList.takeIf { it.isNotEmpty() } ?: throw ErrorCodeException(PARAMETER_INVALID, "repoNameList")
        searchRequest.repoNameList.forEach { repositoryService.checkRepository(searchRequest.projectId, it) }

        val query = nodeSearchQuery(searchRequest)

        val listData = nodeDao.find(query).map { convert(it)!! }
        val count = nodeDao.count(query)

        return Page(searchRequest.page, searchRequest.size, count, listData)
    }

    /**
     * 判断节点是否存在
     */
    fun exist(projectId: String, repoName: String, fullPath: String): Boolean {
        val formattedPath = formatFullPath(fullPath)
        val query = nodeQuery(projectId, repoName, formattedPath)

        return nodeDao.exists(query)
    }

    /**
     * 创建节点，返回id
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun create(createRequest: NodeCreateRequest): IdValue {
        logger.info("create, createRequest: $createRequest")

        val projectId = createRequest.projectId
        val repoName = createRequest.repoName
        val fullPath = parseFullPath(createRequest.fullPath)

        repositoryService.checkRepository(projectId, repoName)
        // 路径唯一性校验
        val existNode = queryModel(projectId, repoName, fullPath)
        if (existNode != null) {
            if (!createRequest.overwrite) throw ErrorCodeException(PARAMETER_IS_EXIST, fullPath)
            else if (existNode.folder || createRequest.folder) throw ErrorCodeException(FOLDER_CANNOT_BE_MODIFIED)
            else {
                // 存在相同路径文件节点且允许覆盖，删除之前的节点
                deleteByPath(projectId, repoName, fullPath, createRequest.operator)
            }
        }
        // 判断父目录是否存在，不存在先创建
        mkdirs(projectId, repoName, getParentPath(fullPath), createRequest.operator)
        // 创建节点
        val node = createRequest.let {
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
        node.blockList = createRequest.blockList?.map { TFileBlock(sequence = it.sequence, sha256 = it.sha256, size = it.size) }
        // 保存节点
        val idValue = IdValue(nodeDao.insert(node).id!!)

        logger.info("Create node [$createRequest] success.")
        return idValue
    }

    /**
     * 重命名文件或者文件夹
     * 重命名过程中出现错误则抛异常，剩下的文件不会再移动
     * 遇到同名文件或者文件夹直接抛异常
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun rename(renameRequest: NodeRenameRequest) {
        val projectId = renameRequest.projectId
        val repoName = renameRequest.repoName
        val fullPath = formatFullPath(renameRequest.fullPath)
        val newFullPath = formatFullPath(renameRequest.newFullPath)

        repositoryService.checkRepository(projectId, repoName)
        val node = queryModel(projectId, repoName, fullPath) ?: throw ErrorCodeException(RepositoryMessageCode.NODE_NOT_FOUND, fullPath)
        doRename(node, newFullPath, renameRequest.operator)

        logger.info("Rename node [$renameRequest] success.")
    }

    /**
     * 移动文件或者文件夹
     * 移动过程中出现错误则抛异常，剩下的文件不会再移动
     * 同名文件移动策略：
     * 1. folder -> folder  跳过
     * 2. folder -> file    出错
     * 3. file -> folder    出错
     * 4. file -> file      overwrite=true覆盖，否则跳过
     * 5. file or folder -> null 创建
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun move(moveRequest: NodeMoveRequest) {
        handleOperateRequest(moveRequest)
    }

    /**
     * 拷贝文件或者文件夹
     * 拷贝过程中出现错误则抛异常，剩下的文件不会再拷贝
     * 同名文件拷贝策略：
     * 1. folder -> folder  跳过
     * 2. folder -> file    出错
     * 3. file -> folder    出错
     * 4. file -> file      overwrite=true覆盖，否则跳过
     * 5. any -> null       创建
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun copy(copyRequest: NodeCopyRequest) {
        handleOperateRequest(copyRequest)
    }

    /**
     * 删除指定节点
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun delete(deleteRequest: NodeDeleteRequest) {
        with(deleteRequest) {
            repositoryService.checkRepository(this.projectId, this.repoName)
            deleteByPath(this.projectId, this.repoName, this.fullPath, this.operator)
        }
    }

    /**
     * 将节点重命名为指定名称
     */
    private fun doRename(node: TNode, newFullPath: String, operator: String) {
        val projectId = node.projectId
        val repoName = node.repoName
        val newPath = getParentPath(newFullPath)
        val newName = getName(newFullPath)

        // 检查新路径是否被占用
        if (exist(projectId, repoName, newFullPath)) {
            logger.warn("Rename node [${node.fullPath}] failed: $newFullPath is exist.")
            throw ErrorCodeException(PARAMETER_IS_EXIST, newFullPath)
        }

        // 如果为文件夹，查询子节点并修改
        if (node.folder) {
            mkdirs(projectId, repoName, newFullPath, operator)
            val newParentPath = formatPath(newFullPath)
            val fullPath = formatPath(node.fullPath)
            val query = nodeListQuery(projectId, repoName, fullPath, includeFolder = true, deep = false)
            nodeDao.find(query).forEach { doRename(it, newParentPath + it.name, operator) }
            // 删除自己
            nodeDao.remove(nodeQuery(projectId, repoName, node.fullPath))
        } else {
            // 修改自己
            val selfQuery = nodeQuery(projectId, repoName, node.fullPath)
            val selfUpdate = nodePathUpdate(newPath, newName, operator)
            nodeDao.updateFirst(selfQuery, selfUpdate)
        }
    }

    /**
     * 检测两个节点在移动或者拷贝时是否存在冲突
     */
    fun checkConflict(node: TNode, existNode: TNode?, overwrite: Boolean) {
        if (existNode == null) return
        if (node.folder && existNode.folder) return
        if (!node.folder && !node.folder && overwrite) return

        logger.warn("Check conflict: [${existNode.fullPath}] is exist and can not be modified")
        throw ErrorCodeException(NOT_SUPPORTED)
    }

    /**
     * 根据全路径删除文件或者目录
     */
    fun deleteByPath(projectId: String, repoName: String, fullPath: String, operator: String, soft: Boolean = true) {
        repositoryService.checkRepository(projectId, repoName)
        val formattedFullPath = formatFullPath(fullPath)
        val formattedPath = formatPath(formattedFullPath)
        val escapedPath = escapeRegex(formattedPath)
        val query = nodeQuery(projectId, repoName)
        query.addCriteria(Criteria().orOperator(
                Criteria.where("fullPath").regex("^$escapedPath"),
                Criteria.where("fullPath").`is`(formattedFullPath)
        ))
        if (soft) {
            // 软删除
            nodeDao.updateMulti(query, nodeDeleteUpdate(operator))
        } else {
            // 硬删除
            nodeDao.remove(query)
        }
        logger.info("Delete node [$projectId/$repoName/$fullPath] by [$operator] success.")
    }

    /**
     * 查询节点model
     */
    private fun queryModel(projectId: String, repoName: String, fullPath: String): TNode? {
        val query = nodeQuery(projectId, repoName, formatFullPath(fullPath), withDetail = true)

        return nodeDao.findOne(query)
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
            nodeDao.insert(TNode(
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

    /**
     * 处理节点操作请求
     */
    private fun handleOperateRequest(request: NodeOperateRequest) {
        val srcProjectId = request.srcProjectId
        val srcRepoName = request.srcRepoName
        val srcFullPath = formatFullPath(request.srcFullPath)

        val destProjectId = request.destProjectId ?: srcProjectId
        val destRepoName = request.destRepoName ?: srcRepoName
        val destPath = formatPath(request.destPath)

        repositoryService.checkRepository(srcProjectId, srcRepoName)
        repositoryService.checkRepository(destProjectId, destRepoName)

        val node = queryModel(srcProjectId, srcRepoName, srcFullPath) ?: throw ErrorCodeException(RepositoryMessageCode.NODE_NOT_FOUND, srcFullPath)

        // 确保目的目录是否存在
        val destPathNode = queryModel(destProjectId, destRepoName, destPath)
        if (destPathNode != null && !destPathNode.folder) {
            // 目的节点存在且为文件，出错
            if (!destPathNode.folder) {
                logger.warn("[${request.getOperateName()}] node [${node.fullPath}] failed: Destination path: [$destPath] is exist and is a file.")
                throw ErrorCodeException(NOT_SUPPORTED)
            }
        } else {
            // 创建新路径
            mkdirs(destProjectId, destRepoName, destPath, request.operator)
        }
        // 递归操作
        operateNode(node, destPath, request)

        logger.info("[${request.getOperateName()}] node success: [$request]")
    }

    /**
     * 操作节点
     */
    private fun operateNode(node: TNode, destPath: String, request: NodeOperateRequest) {
        val projectId = node.projectId
        val repoName = node.repoName
        val overwrite = request.overwrite
        val destProjectId = request.destProjectId ?: node.projectId
        val destRepoName = request.destRepoName ?: node.repoName
        val operator = request.operator

        // 确保目的节点路径不冲突
        val existNode = queryModel(projectId, repoName, destPath + node.name)
        checkConflict(node, existNode, overwrite)

        // 目录深度优先遍历，确保出错时，源节点结构不受影响
        if (node.folder) {
            // 如果待复制的节点为目录，则在目的路径创建同名目录
            if (existNode == null) {
                mkdirs(destProjectId, destRepoName, formatPath(destPath + node.name), operator)
            }
            val formattedPath = formatPath(node.fullPath)
            val destSubPath = formatPath(destPath + node.name)
            val query = nodeListQuery(projectId, repoName, formattedPath, includeFolder = true, deep = false)
            val subNodes = nodeDao.find(query)
            subNodes.forEach { operateNode(it, destSubPath, request) }

            // 文件移动时，目的路径的目录已经自动创建，因此删除源目录
            if(request is NodeMoveRequest) {
                nodeDao.remove(nodeQuery(projectId, repoName, node.fullPath))
            }
        } else {
            // 如果待移动的节点为文件且存在冲突&允许覆盖，删除目的节点
            if (existNode != null && overwrite) {
                val query = nodeQuery(destProjectId, destRepoName, existNode.fullPath)
                val update = nodeDeleteUpdate(operator)
                nodeDao.updateMulti(query, update)
            }
            when(request) {
                is NodeMoveRequest -> {
                    // 移动节点
                    val selfQuery = nodeQuery(projectId, repoName, node.fullPath)
                    val selfUpdate = nodeRepoUpdate(destProjectId, destRepoName, destPath, node.name, operator)
                    nodeDao.updateFirst(selfQuery, selfUpdate)
                }
                is NodeCopyRequest -> {
                    // copy自身
                    val newNode = node.copy(
                            id = null,
                            path = destPath,
                            fullPath = destPath + node.name,
                            projectId = destProjectId,
                            repoName = destRepoName,
                            createdBy = operator,
                            createdDate = LocalDateTime.now(),
                            lastModifiedBy = operator,
                            lastModifiedDate = LocalDateTime.now()
                    )
                    nodeDao.insert(newNode)
                }
            }
        }
    }

    /**
     * 根据有效天数，计算到期时间
     */
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
