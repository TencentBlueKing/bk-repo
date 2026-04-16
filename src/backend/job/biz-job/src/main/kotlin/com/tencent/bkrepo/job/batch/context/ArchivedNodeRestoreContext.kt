package com.tencent.bkrepo.job.batch.context

import java.time.LocalDateTime

/**
 * SEPARATE_ARCHIVED 项目与分离日期在单个 collection 扫描周期内只加载一次，供按 sha256 查冷表节点复用。
 */
class ArchivedNodeRestoreContext(
    var separateArchivedProjectDates: Map<String, Set<LocalDateTime>> = emptyMap()
) : NodeContext()
