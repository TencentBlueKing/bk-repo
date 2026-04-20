package com.tencent.bkrepo.common.metadata.service.separation

import java.time.LocalDateTime

/**
 * 热表联动或管理接口手工清理冷表时，给冷表对应节点打 deleted 字段。
 * 路径删除与按时间清理语义不同，分两个入口，避免混用。
 */

interface SeparationColdPurgeService {

    /** 删除 API / 按路径批量删 / 按 id 删节点等：每个元素为热表上的删除根路径（文件或目录）。 */
    fun markColdDeletedAfterHotPathSoftDelete(
        projectId: String,
        repoName: String,
        hotDeleteRootFullPaths: Collection<String>,
        deletedAt: LocalDateTime,
    )



    /**
     * 按时间清理（deleteBeforeDate）：在 clean 作用域根路径下，仅对与热表相同时间条件的冷表文件节点打 deleted。
     * [cleanBeforeDate] 与热表接口的 date 一致；[deletedAt] 为写入 deleted 字段的时间（通常即热表软删时间）。
     */
    fun markColdDeletedAfterHotNodeClean(
        projectId: String,
        repoName: String,
        cleanScopeRootFullPath: String,
        cleanBeforeDate: LocalDateTime,
        deletedAt: LocalDateTime,
    )

    /** 管理接口按路径手工清理冷表。 */
    fun markColdDeletedByApiPathDelete(
        projectId: String,
        repoName: String,
        coldDeleteRootFullPaths: Collection<String>,
        deletedAt: LocalDateTime,
    )

    /** 管理接口按时间条件手工清理冷表。 */
    fun markColdDeletedByApiNodeClean(
        projectId: String,
        repoName: String,
        cleanScopeRootFullPath: String,
        cleanBeforeDate: LocalDateTime,
        deletedAt: LocalDateTime,
    )

}

