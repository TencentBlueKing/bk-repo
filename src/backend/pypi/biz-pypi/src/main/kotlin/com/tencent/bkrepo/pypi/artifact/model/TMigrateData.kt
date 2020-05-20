package com.tencent.bkrepo.pypi.artifact.model

import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingDocument
import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingKey
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import java.time.LocalDateTime

@ShardingDocument("migration_data")
@CompoundIndexes(
        CompoundIndex(name = "migration_data_idx", def = "{'id': 1, 'projectId': 1, 'repoName': 1}", background = true, unique = true)
)
data class TMigrateData(
        var id: String? = null,
        // 错误数据
        var errorData: String?,
        var createdBy: String,
        var createdDate: LocalDateTime,
        var lastModifiedBy: String,
        var lastModifiedDate: LocalDateTime,
        var packagesNum: Int,
        var filesNum: Int,
        var elapseTimeSeconds: Long,
        var description: String,

        @ShardingKey(count = SHARDING_COUNT)
        var projectId: String,
        var repoName: String
)