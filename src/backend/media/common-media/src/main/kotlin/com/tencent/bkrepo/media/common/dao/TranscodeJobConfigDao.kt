package com.tencent.bkrepo.media.common.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJobConfig
import org.springframework.stereotype.Repository

@Repository
class TranscodeJobConfigDao : SimpleMongoDao<TMediaTranscodeJobConfig>()