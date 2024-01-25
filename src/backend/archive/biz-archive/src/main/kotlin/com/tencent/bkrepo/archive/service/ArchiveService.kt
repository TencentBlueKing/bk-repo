package com.tencent.bkrepo.archive.service

import com.tencent.bkrepo.archive.pojo.ArchiveFile
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.CreateArchiveFileRequest

/**
 * 归档服务
 * */
interface ArchiveService {

    /**
     * 归档文件
     * */
    fun archive(request: CreateArchiveFileRequest)

    /**
     * 删除归档文件
     * */
    fun delete(request: ArchiveFileRequest)

    /**
     * 恢复文件
     * */
    fun restore(request: ArchiveFileRequest)

    /**
     * 完成归档
     * */
    fun complete(request: ArchiveFileRequest)

    /**
     * 获取归档任务
     * */

    fun get(sha256: String, storageCredentialsKey: String?): ArchiveFile?
}
