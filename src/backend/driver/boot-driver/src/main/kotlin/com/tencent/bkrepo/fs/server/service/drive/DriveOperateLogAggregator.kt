package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.metadata.pojo.log.OperateLog

object DriveOperateLogAggregator {

    /**
     * 在同一 batch 内按聚合 key 合并可聚合类型的审计日志。
     */
    fun aggregate(logs: List<OperateLog>, aggregatableTypes: Set<String>): List<OperateLog> {
        if (logs.isEmpty()) {
            return logs
        }
        val merged = LinkedHashMap<AggregationKey, MutableList<OperateLog>>()
        val passthrough = ArrayList<OperateLog>()
        for (log in logs) {
            if (!aggregatableTypes.contains(log.type)) {
                passthrough.add(log)
                continue
            }
            merged.getOrPut(AggregationKey.from(log)) { ArrayList() }.add(log)
        }
        val aggregated = merged.values.map { mergeBucket(it) }
        return passthrough + aggregated
    }

    private fun mergeBucket(bucket: List<OperateLog>): OperateLog {
        if (bucket.size == 1) {
            return bucket.first()
        }
        val first = bucket.first()
        val last = bucket.last()
        val mergedDescription = LinkedHashMap<String, Any>(last.description)
        mergedDescription["merged"] = true
        mergedDescription["count"] = bucket.size
        mergedDescription["firstOperateTime"] = first.createdDate.toString()
        mergedDescription["lastOperateTime"] = last.createdDate.toString()
        return OperateLog(
            createdDate = last.createdDate,
            type = last.type,
            projectId = last.projectId,
            repoName = last.repoName,
            resourceKey = last.resourceKey,
            userId = last.userId,
            clientAddress = last.clientAddress,
            description = mergedDescription,
        )
    }

    private data class AggregationKey(
        val userId: String,
        val type: String,
        val projectId: String?,
        val repoName: String?,
        val resourceKey: String,
    ) {
        companion object {
            fun from(log: OperateLog): AggregationKey {
                return AggregationKey(
                    userId = log.userId,
                    type = log.type,
                    projectId = log.projectId,
                    repoName = log.repoName,
                    resourceKey = log.resourceKey,
                )
            }
        }
    }
}
