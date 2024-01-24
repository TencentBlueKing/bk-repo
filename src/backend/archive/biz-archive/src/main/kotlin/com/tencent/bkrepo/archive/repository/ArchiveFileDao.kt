package com.tencent.bkrepo.archive.repository

import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.stereotype.Repository

@Repository
class ArchiveFileDao : SimpleMongoDao<TArchiveFile>()
