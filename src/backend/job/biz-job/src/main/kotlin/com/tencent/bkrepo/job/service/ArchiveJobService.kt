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

    /**
     * 迁移已归档制品
     *
     * @param context 迁移上下文
     * @param node 待迁移制品
     *
     * @return 是否迁移成功，返回true表示迁移成功，false表示文件未归档，抛出异常表示不支持迁移的归档状态
     *
     * @throws IllegalStateException 处于不支持迁移的状态时抛出该异常
     */
    fun migrateArchivedFile(context: MigrationContext, node: Node): Boolean
}
