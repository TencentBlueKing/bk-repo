package com.tencent.bkrepo.repository.dao

import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingMongoDao
import com.tencent.bkrepo.repository.model.TFileReference
import org.springframework.stereotype.Repository

/**
 * 文件摘要引用 Dao
 */
@Repository
class FileReferenceDao : ShardingMongoDao<TFileReference>()
