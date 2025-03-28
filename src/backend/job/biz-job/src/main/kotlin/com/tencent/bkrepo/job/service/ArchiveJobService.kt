package com.tencent.bkrepo.job.service

import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.pojo.Node
import com.tencent.bkrepo.job.pojo.ArchiveRestoreRequest

/**
 * 归档任务服务
 * */
interface ArchiveJobService {

    /**
     * 归档项目文件
     * @param projectId 项目id
     * @param key 归档存储的key
     * @param days 归档天数，多久之前的文件需要归档
     * @param storageClass 归档存储类型
     * */
    fun archive(projectId: String, key: String, days: Int, storageClass: ArchiveStorageClass)

    /**
     * 恢复文件，从归档或者压缩中恢复文件
     * */
    fun restore(request: ArchiveRestoreRequest)
}
