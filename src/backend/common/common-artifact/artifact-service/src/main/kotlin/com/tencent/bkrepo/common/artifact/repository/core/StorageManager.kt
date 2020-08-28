package com.tencent.bkrepo.common.artifact.repository.core

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 存储Manager
 *
 * 虽然[StorageService]提供了构件存储服务，但我们保存一个文件节点需要两步操作:
 *   1. [StorageService]保存文件数据
 *   2. [NodeClient]微服务调用创建文件节点
 * 这样会存在几个问题:
 *   1. 每个地方都会进行同样的操作，增加代码重复率
 *   2. 不支持事务，如果文件保存成功，但节点创建失败，会导致产生垃圾文件并且无法清理
 *
 * 所以提供StorageManager，简化依赖源的操作并减少错误率
 */
@Component
class StorageManager(
    private val storageService: StorageService,
    private val nodeClient: NodeClient
) {

    /**
     * 存储构件[artifactFile]到[storageCredentials]上，并根据[request]创建节点
     * 操作成功返回节点详情[NodeDetail]
     */
    fun storeArtifactFile(
        request: NodeCreateRequest,
        artifactFile: ArtifactFile,
        storageCredentials: StorageCredentials?
    ): NodeDetail {
        val affectedCount = storageService.store(request.sha256!!, artifactFile, storageCredentials)
        try {
            return nodeClient.create(request).data!!
        } catch (exception: Exception) {
            // 当文件有创建，则删除文件
            if (affectedCount == 1) {
                try {
                    storageService.delete(request.sha256!!, storageCredentials)
                } catch (exception: Exception) {
                    logger.error("Failed to remove added file", exception)
                }
            }
            // 异常往上抛
            throw exception
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StorageManager::class.java)
    }
}