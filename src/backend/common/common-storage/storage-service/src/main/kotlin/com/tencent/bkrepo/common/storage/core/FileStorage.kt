package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.storage.pojo.StorageCredentials
import java.io.File
import java.io.InputStream

/**
 * 文件存储接口
 * hash可理解为文件id或文件摘要
 * Credentials代表存储身份，用作缓存key；Client为具体的存储客户端
 *
 * @author: carrypan
 * @date: 2019-09-09
 */
interface FileStorage {

    /**
     * 存储文件
     */
    fun store(hash: String, inputStream: InputStream, storageCredentials: StorageCredentials? = null)

    /**
     * 追加文件
     */
    fun append(path: String, filename: String, inputStream: InputStream, storageCredentials: StorageCredentials? = null)

    /**
     * 加载文件
     */
    fun load(hash: String, storageCredentials: StorageCredentials? = null): File?

    /**
     * 删除文件
     */
    fun delete(hash: String, storageCredentials: StorageCredentials? = null)

    /**
     * 判断是否存在
     */
    fun exist(hash: String, storageCredentials: StorageCredentials? = null): Boolean

    /**
     * 创建分块文件夹
     */
    fun makeBlockPath(path: String, storageCredentials: StorageCredentials? = null)

    /**
     * 检查分块目录是否存在
     */
    fun checkBlockPath(path: String, storageCredentials: StorageCredentials? = null): Boolean

    /**
     * 删除分块文件
     */
    fun deleteBlockPath(path: String, storageCredentials: StorageCredentials? = null)

    /**
     * 列出分块文件
     */
    fun listBlockInfo(path: String, storageCredentials: StorageCredentials? = null): List<Pair<Long, String>>

    /**
     * 存储分块文件
     */
    fun storeBlock(path: String, sequence: Int, sha256: String, inputStream: InputStream, storageCredentials: StorageCredentials? = null)

    /**
     * 组合分块文件
     */
    fun combineBlock(path: String, storageCredentials: StorageCredentials? = null): File
}
