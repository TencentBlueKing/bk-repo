package com.tencent.bkrepo.scanner.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.scanner.model.TSpdxLicense
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class SpdxLicenseDao : SimpleMongoDao<TSpdxLicense>() {
    fun findByLicenseId(licenseId: String): TSpdxLicense? {
        return this.findOne(Query(TSpdxLicense::licenseId.isEqualTo(licenseId)))
    }
}
