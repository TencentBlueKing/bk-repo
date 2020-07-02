package com.tencent.bkrepo.repository.model

import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingDocument
import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingKey
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes

/**
 * 文件摘要引用
 *
 * @author: carrypan
 * @date: 2019/11/12
 */
@ShardingDocument("file_reference")
@CompoundIndexes(
        CompoundIndex(name = "sha256_idx", def = "{'sha256': 1}", background = true),
        CompoundIndex(name = "count_idx", def = "{'count': 1}", background = true)
)
data class TFileReference(
    var id: String? = null,
    @ShardingKey(count = SHARDING_COUNT)
    var sha256: String,
    var credentialsKey: String? = null,
    var count: Long
)
