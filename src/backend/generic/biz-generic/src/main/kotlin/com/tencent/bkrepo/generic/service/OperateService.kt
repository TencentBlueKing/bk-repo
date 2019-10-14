package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.generic.constant.REPO_TYPE
import com.tencent.bkrepo.generic.pojo.FileDetail
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.node.Node
import com.tencent.bkrepo.repository.pojo.node.NodeSearchRequest
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
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    fun getFileSize(userId: String, projectId: String, repoName: String, fullPath: String): Long {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    fun mkdir(userId: String, projectId: String, repoName: String, fullPath: String) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    fun move(userId: String, projectId: String, repoName: String, fullPath: String, toPath: String) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    fun copy(userId: String, projectId: String, repoName: String, fullPath: String, toProjectId: String, toRepoName: String, toPath: String) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OperateService::class.java)

        fun toFileInfo(node: Node): FileInfo {
            return node.let { FileInfo(
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
            )}
        }
    }
}
