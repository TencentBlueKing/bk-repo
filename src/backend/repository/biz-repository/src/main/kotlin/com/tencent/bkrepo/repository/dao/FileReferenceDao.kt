package com.tencent.bkrepo.repository.dao

import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingMongoDao
import com.tencent.bkrepo.repository.model.TFileReference
import org.springframework.stereotype.Repository

/**
 * 文件摘要引用 Dao
 *
 * @author: carrypan
 * @date: 2019/11/7
 */
@Repository
class FileReferenceDao : ShardingMongoDao<TFileReference>()
