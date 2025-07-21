package com.tencent.bkrepo.repository.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.repository.model.TScheduleLoad
import org.springframework.stereotype.Repository

@Repository
class ScheduleLoadDao : SimpleMongoDao<TScheduleLoad>()