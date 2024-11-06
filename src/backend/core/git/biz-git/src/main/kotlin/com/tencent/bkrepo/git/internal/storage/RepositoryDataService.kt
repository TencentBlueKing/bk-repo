package com.tencent.bkrepo.git.internal.storage

import org.eclipse.jgit.internal.storage.dfs.DfsOutputStream
import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription
import org.eclipse.jgit.internal.storage.dfs.ReadableChannel

/**
 * 仓库数据服务
 * */
interface RepositoryDataService {

    /**
     * 保存pack文件描述符
     * */
    fun savePackDescriptions(
        repository: CodeRepository,
        desc: Collection<DfsPackDescription>,
        replace: Collection<DfsPackDescription>?
    )

    /**
     * 删除pack文件描述符
     * */
    fun deletePackDescriptions(repository: CodeRepository, desc: Collection<DfsPackDescription>)

    /**
     * 查找pack文件描述符
     * */
    fun listPackDescriptions(repository: CodeRepository): List<DfsPackDescription>

    /**
     * 获取仓库文件数据
     * */
    fun getReadableChannel(repository: CodeRepository, fileName: String, blockSize: Int): ReadableChannel

    /**
     * 获取仓库文件输出流
     * */
    fun getOutputStream(repository: CodeRepository, fileName: String): DfsOutputStream
}
