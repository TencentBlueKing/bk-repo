package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.metadata.model.TFileReference
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

object FileReferenceQueryHelper {

    fun buildQuery(sha256: String, credentialsKey: String?, gt: Int? = null): Query {
        val criteria = Criteria.where(TFileReference::sha256.name).`is`(sha256)
        criteria.and(TFileReference::credentialsKey.name).`is`(credentialsKey)
        gt?.let { criteria.and(TFileReference::count.name).gt(gt) }
        return Query.query(criteria)
    }
}
