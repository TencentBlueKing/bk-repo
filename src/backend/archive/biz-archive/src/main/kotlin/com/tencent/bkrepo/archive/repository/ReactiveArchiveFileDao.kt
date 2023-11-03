package com.tencent.bkrepo.archive.repository

import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.common.mongo.reactive.dao.SimpleMongoReactiveDao
import org.springframework.stereotype.Repository

@Repository
class ReactiveArchiveFileDao : SimpleMongoReactiveDao<TArchiveFile>()
