package com.tencent.bkrepo.archive.utils

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.common.mongo.constant.ID
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

object ArchiveFileQueryHelper {
    fun buildQuery(status: ArchiveStatus, id: String, limit: Int): Query {
        val criteria = where(TArchiveFile::status).isEqualTo(status.name)
            .and(ID).gt(ObjectId(id))
        return Query.query(criteria)
            .limit(limit)
            .with(Sort.by(ID).ascending())
    }
}
