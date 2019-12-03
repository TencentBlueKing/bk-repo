package com.tencent.bkrepo.common.storage.cache

import com.tencent.bkrepo.common.storage.schedule.CleanupResult
import java.io.File
import java.io.InputStream

/**
 * 文件缓存接口
 *
 * @author: carrypan
 * @date: 2019/10/29
 */
interface FileCache {
    /**
     * 缓存文件
     */
    fun cache(path: String, filename: String, inputStream: InputStream): File

    /**
     * 获取缓存文件
     */
    fun get(path: String, filename: String): File?

    /**
     * 移除缓存文件
     */
    fun remove(path: String, filename: String)

    /**
     * 创建新缓存文件
     */
    fun touch(path: String, filename: String): File

    /**
     * 判断文件是否存在
     */
    fun exist(path: String, filename: String): Boolean

    /**
     * 创建分块目录
     */
    fun makeBlockPath(path: String)

    /**
     * 检查分块目录是否存在
     */
    fun checkBlockPath(path: String): Boolean

    /**
     * 删除分块目录
     */
    fun deleteBlockPath(path: String)

    /**
     * 追加写文件
     */
    fun append(path: String, filename: String, inputStream: InputStream)

    /**
     * 存储分块文件
     */
    fun storeBlock(path: String, sequence: Int, sha256: String, inputStream: InputStream)

    /**
     * 合并分块文件
     */
    fun combineBlock(path: String): File

    /**
     * 列出分块
     */
    fun listBlockInfo(path: String): List<Pair<Long, String>>

    /**
     * 清理缓存文件
     */
    fun onClean(): CleanupResult

}
