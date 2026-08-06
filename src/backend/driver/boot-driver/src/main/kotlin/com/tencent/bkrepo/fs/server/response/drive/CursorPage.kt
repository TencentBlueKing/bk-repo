package com.tencent.bkrepo.fs.server.response.drive

data class CursorPage<out T>(
    val pageSize: Int,
    val hasMore: Boolean,
    val records: List<T>,
) {
    companion object {
        fun <T, R> fromRecords(records: List<T>, pageSize: Int, transform: (T) -> R): CursorPage<R> {
            // 调用方会按 pageSize + 1 查询；超过 pageSize 表示仍有下一页。
            val hasMore = records.size > pageSize
            val pageRecords = if (hasMore) records.subList(0, pageSize) else records
            return CursorPage(
                pageSize = pageSize,
                hasMore = hasMore,
                records = pageRecords.map(transform),
            )
        }
    }
}
