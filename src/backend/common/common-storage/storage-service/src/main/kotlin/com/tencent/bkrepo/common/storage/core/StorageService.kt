package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.check.SynchronizeResult
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupResult
import com.tencent.bkrepo.common.storage.pojo.FileInfo

/**
 * 存储服务接口
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
interface StorageService {
    /**
     * 存储文件
     */
    fun store(digest: String, artifactFile: ArtifactFile, storageCredentials: StorageCredentials? = null)

    /**
     * 加载文件
     */
    fun load(digest: String, range: Range, storageCredentials: StorageCredentials? = null): ArtifactInputStream?

    /**
     * 删除文件
     */
    fun delete(digest: String, storageCredentials: StorageCredentials? = null)

    /**
     * 判断是否存在
     */
    fun exist(digest: String, storageCredentials: StorageCredentials? = null): Boolean

    /**
     * 创建可追加的文件, 返回文件追加Id
     */
    fun createAppendId(): String

    /**
     * 追加文件，返回当前文件长度
     * appendId: 文件追加Id
     */
    fun append(appendId: String, artifactFile: ArtifactFile): Long

    /**
     * 结束追加，存储并返回完整文件
     * appendId: 文件追加Id
     */
    fun finishAppend(appendId: String, storageCredentials: StorageCredentials? = null): FileInfo

    /**
     * 创建分块存储目录，返回分块存储Id
     */
    fun createBlockId(): String

    /**
     * 删除分块文件
     * blockId: 分块存储id
     */
    fun deleteBlockId(blockId: String)

    /**
     * 检查blockId是否存在
     * blockId: 分块存储id
     */
    fun checkBlockId(blockId: String): Boolean

    /**
     * 列出分块文件
     * blockId: 分块存储id
     */
    fun listBlock(blockId: String): List<Pair<Long, String>>

    /**
     * 存储分块文件
     * blockId: 分块存储id
     */
    fun storeBlock(
        blockId: String,
        sequence: Int,
        digest: String,
        artifactFile: ArtifactFile,
        overwrite: Boolean
    )

    /**
     * 合并分块文件
     * blockId: 分块存储id
     */
    fun mergeBlock(blockId: String, storageCredentials: StorageCredentials? = null): FileInfo

    /**
     * 清理文件
     */
    fun cleanUp(): CleanupResult

    /**
     * 手动补偿上传文件
     */
    fun manualRetry(digest: String, storageCredentials: StorageCredentials? = null)

    /**
     * 检验缓存文件一致性
     */
    fun synchronizeFile(storageCredentials: StorageCredentials? = null): SynchronizeResult

    /**
     * 健康检查
     */
    fun checkHealth(storageCredentials: StorageCredentials? = null)
}
