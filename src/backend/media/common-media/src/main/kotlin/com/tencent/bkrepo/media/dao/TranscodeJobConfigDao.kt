package com.tencent.bkrepo.media.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.media.model.TMediaTranscodeJobConfig
import org.springframework.stereotype.Repository

@Repository
class TranscodeJobConfigDao : SimpleMongoDao<TMediaTranscodeJobConfig>()