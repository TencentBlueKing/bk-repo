package com.tencent.bkrepo.common.storage.core

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
    fun store(hash: String, inputStream: InputStream, clientCredentials: ClientCredentials? = null)

    /**
     * 追加文件
     */
    fun append(path: String, filename: String, inputStream: InputStream, clientCredentials: ClientCredentials? = null)

    /**
     * 加载文件
     */
    fun load(hash: String, clientCredentials: ClientCredentials? = null): File?

    /**
     * 删除文件
     */
    fun delete(hash: String, clientCredentials: ClientCredentials? = null)

    /**
     * 判断是否存在
     */
    fun exist(hash: String, clientCredentials: ClientCredentials? = null): Boolean

    /**
     * 创建分块文件夹
     */
    fun makeBlockPath(path: String, clientCredentials: ClientCredentials? = null)

    /**
     * 检查分块目录是否存在
     */
    fun checkBlockPath(path: String, clientCredentials: ClientCredentials? = null): Boolean

    /**
     * 删除分块文件
     */
    fun deleteBlockPath(path: String, clientCredentials: ClientCredentials? = null)

    /**
     * 列出分块文件
     */
    fun listBlockInfo(path: String, clientCredentials: ClientCredentials? = null): List<Pair<Long, String>>

    /**
     * 存储分块文件
     */
    fun storeBlock(path: String, sequence: Int, sha256: String, inputStream: InputStream, clientCredentials: ClientCredentials? = null)

    /**
     * 组合分块文件
     */
    fun combineBlock(path: String, clientCredentials: ClientCredentials? = null): File
}
