/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.analyst.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.analyst.model.TSpdxLicense
import com.tencent.bkrepo.common.api.util.EscapeUtils
import org.bson.BsonRegularExpression
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class SpdxLicenseDao : SimpleMongoDao<TSpdxLicense>() {
    fun findByLicenseId(licenseId: String): TSpdxLicense? {
        return this.findOne(Query(TSpdxLicense::licenseId.isEqualTo(licenseId)))
    }

    fun findByLicenseIds(licenseIds: List<String>, ignoreCase: Boolean = false): List<TSpdxLicense> {
        val licenseIdExpression = if (ignoreCase) {
            // 忽略licenseId大小写查询，{ licenseId : { $in : [ '/^apache-2.0$/i' ] } }
            licenseIds.map { BsonRegularExpression("^${EscapeUtils.escapeRegex(it)}$", "i") }
        } else {
            licenseIds
        }
        return find(Query(TSpdxLicense::licenseId.inValues(licenseIdExpression)))
    }
}
