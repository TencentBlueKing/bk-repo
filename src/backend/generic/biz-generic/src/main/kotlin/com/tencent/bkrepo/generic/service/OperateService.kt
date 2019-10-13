package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.generic.pojo.FileDetail
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
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
    fun listFile(userId: String, projectId: String, repoName: String, fullPath: String, includeFolder: Boolean, deep: Boolean): List<FileInfo> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    fun searchFile(userId: String, projectId: String, repoName: String, pathPattern: List<String>, metadataCondition: Map<String, String>): List<FileInfo> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
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
    }
}
