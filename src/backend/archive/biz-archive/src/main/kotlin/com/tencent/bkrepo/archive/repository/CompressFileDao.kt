package com.tencent.bkrepo.archive.repository

import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.stereotype.Repository

@Repository
class CompressFileDao : SimpleMongoDao<TCompressFile>()
