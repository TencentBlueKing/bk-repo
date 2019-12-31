package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import java.io.File

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
     * 存储文件
     */
    fun store(digest: String, file: File, storageCredentials: StorageCredentials? = null)

    /**
     * 加载文件
     */
    fun load(digest: String, storageCredentials: StorageCredentials? = null): File?

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
     * 追加文件
     * appendId: 文件追加Id
     */
    fun append(appendId: String, artifactFile: ArtifactFile)

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
    fun storeBlock(blockId: String, sequence: Int, digest: String, artifactFile: ArtifactFile)

    /**
     * 合并分块文件
     * blockId: 分块存储id
     */
    fun mergeBlock(blockId: String, storageCredentials: StorageCredentials? = null): FileInfo
}
