package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.fs.server.model.drive.TDriveBlockNode
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import com.tencent.bkrepo.fs.server.RepositoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CoDriveStorageManager(
    private val driveBlockNodeService: DriveBlockNodeService,
    private val storageService: StorageService,
    private val driveFileReferenceService: DriveFileReferenceService
) {

    suspend fun storeBlock(artifactFile: ArtifactFile, blockNode: TDriveBlockNode) {
        withContext(Dispatchers.IO + currentCoroutineContext()) {
            val digest = artifactFile.getFileSha256()
            val repo = RepositoryCache.Companion.getRepoDetail(blockNode.projectId, blockNode.repoName)
            val storageCredentials = repo.storageCredentials
            val stored = storageService.store(digest, artifactFile, storageCredentials)
            try {
                driveBlockNodeService.createBlock(blockNode, storageCredentials)
            } catch (e: Exception) {
                if (stored == 1) {
                    try {
                        // 当createNode调用超时，实际node和引用创建成功时不会做任何改变
                        // 当文件创建成功，但是node创建失败时，则创建一个计数为0的fileReference用于清理任务清理垃圾文件
                        driveFileReferenceService.increment(digest, storageCredentials?.key, 0L)
                    } catch (exception: Exception) {
                        // 创建引用失败时需要添加定时任务清理存储中未被引用的数据
                        logger.error("Failed to create ref for new created file[$digest]", exception)
                    }
                }
                throw e
            }
        }
    }

    suspend fun loadArtifactInputStream(
        blocks: List<RegionResource>,
        range: Range,
        storageCredentials: StorageCredentials?
    ): ArtifactInputStream? {
        return withContext(Dispatchers.IO + currentCoroutineContext()) {
            try {
                storageService.load(blocks, range, storageCredentials)
            } catch (e: Exception) {
                logger.error("load drive blocks input stream failed", e)
                null
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CoDriveStorageManager::class.java)
    }
}
