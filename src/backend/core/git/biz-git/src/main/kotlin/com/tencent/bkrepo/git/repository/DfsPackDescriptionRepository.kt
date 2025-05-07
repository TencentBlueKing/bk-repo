package com.tencent.bkrepo.git.repository

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.git.model.TDfsPackDescription
import org.springframework.stereotype.Repository

@Repository
class DfsPackDescriptionRepository : SimpleMongoDao<TDfsPackDescription>()
