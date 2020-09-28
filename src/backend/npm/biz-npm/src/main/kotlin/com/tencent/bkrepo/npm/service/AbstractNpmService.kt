package com.tencent.bkrepo.npm.service

import com.tencent.bkrepo.npm.exception.NpmRepoNotFoundException
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractNpmService {

    @Autowired
    lateinit var nodeClient: NodeClient

    @Autowired
    lateinit var repositoryClient: RepositoryClient

    @Autowired
    lateinit var packageClient: PackageClient

    /**
     * 查询仓库是否存在
     */
    fun checkRepositoryExist(projectId: String, repoName: String) {
        repositoryClient.getRepoDetail(projectId, repoName, "NPM").data ?: run {
            logger.error("check repository [$repoName] in projectId [$projectId] failed!")
            throw NpmRepoNotFoundException("repository [$repoName] in projectId [$projectId] not existed.")
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(AbstractNpmService::class.java)
    }
}