package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.generic.constant.REPO_TYPE
import com.tencent.bkrepo.generic.pojo.FileDetail
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.FileSizeInfo
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeSearchRequest
import com.tencent.bkrepo.repository.pojo.node.NodeUpdateRequest
import com.tencent.bkrepo.repository.util.NodeUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 文件操作服务类
 *
 * @author: carrypan
 * @date: 2019-10-13
 */
@Service
class OperateService(
    private val repositoryResource: RepositoryResource,
    private val nodeResource: NodeResource
) {
    fun listFile(userId: String, projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): List<FileInfo> {
        // TODO: 鉴权
        val repository = repositoryResource.query(projectId, repoName, REPO_TYPE).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        return nodeResource.list(repository.id, path, includeFolder, deep).data?.map { toFileInfo(it) } ?: emptyList()
    }

    fun searchFile(userId: String, projectId: String, repoName: String, pathPattern: List<String>, metadataCondition: Map<String, String>): List<FileInfo> {
        // TODO: 鉴权
        val repository = repositoryResource.query(projectId, repoName, REPO_TYPE).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        return nodeResource.search(repository.id, NodeSearchRequest(pathPattern, metadataCondition)).data?.map { toFileInfo(it) } ?: emptyList()
    }

    fun getFileDetail(userId: String, projectId: String, repoName: String, fullPath: String): FileDetail {
        // TODO: 鉴权
        val repository = repositoryResource.query(projectId, repoName, REPO_TYPE).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        val nodeDetail = nodeResource.queryDetail(repository.id, fullPath).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
        return FileDetail(toFileInfo(nodeDetail.nodeInfo), nodeDetail.metadata)
    }

    fun getFileSize(userId: String, projectId: String, repoName: String, fullPath: String): FileSizeInfo {
        // TODO: 鉴权
        val repository = repositoryResource.query(projectId, repoName, REPO_TYPE).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        val nodeSizeInfo = nodeResource.getNodeSize(repository.id, fullPath).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)
        return FileSizeInfo(subFileCount = nodeSizeInfo.subNodeCount, size = nodeSizeInfo.size)
    }

    fun mkdir(userId: String, projectId: String, repoName: String, fullPath: String) {
        // TODO: 鉴权
        val repository = repositoryResource.query(projectId, repoName, REPO_TYPE).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        val formattedFullPath = NodeUtils.formatFullPath(fullPath)
        val existNode = nodeResource.queryDetail(repository.id, formattedFullPath).data
        if (existNode != null) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_EXIST, fullPath)
        }

        val path = NodeUtils.getParentPath(formattedFullPath)
        val name = NodeUtils.getName(formattedFullPath)
        val createRequest = NodeCreateRequest(
            folder = true,
            path = path,
            name = name,
            repositoryId = repository.id,
            createdBy = userId
        )

        val result = nodeResource.create(createRequest)

        if (result.isNotOk()) {
            logger.warn("user[$userId] mkdirs [$projectId/${repoName}$formattedFullPath] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }

        logger.info("user[$userId] mkdirs [$projectId/${repoName}$formattedFullPath] success")
    }

    fun move(userId: String, projectId: String, repoName: String, fullPath: String, toPath: String) {
        // TODO: 鉴权
        val repository = repositoryResource.query(projectId, repoName, REPO_TYPE).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        val existNode = nodeResource.queryDetail(repository.id, fullPath).data ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, fullPath)

        val formattedToPath = NodeUtils.formatFullPath(toPath)
        val updateRequest = NodeUpdateRequest(
                path = NodeUtils.getParentPath(formattedToPath),
                name = NodeUtils.getName(formattedToPath),
                modifiedBy = userId
        )
        val result = nodeResource.update(existNode.nodeInfo.id, updateRequest)

        if (result.isNotOk()) {
            logger.warn("user[$userId] move [$projectId/${repoName}$fullPath] to [$toPath] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }
    }

    fun copy(userId: String, projectId: String, repoName: String, fullPath: String, toProjectId: String, toRepoName: String, toPath: String) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OperateService::class.java)

        fun toFileInfo(nodeInfo: NodeInfo): FileInfo {
            return nodeInfo.let { FileInfo(
                    createdBy = it.createdBy,
                    createdDate = it.createdDate,
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate,
                    folder = it.folder,
                    path = it.path,
                    name = it.name,
                    fullPath = it.fullPath,
                    size = it.size,
                    sha256 = it.sha256
            ) }
        }
    }
}
