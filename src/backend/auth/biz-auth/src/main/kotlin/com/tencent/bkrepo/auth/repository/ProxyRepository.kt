/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TProxy
import com.tencent.bkrepo.auth.pojo.proxy.ProxyListOption
import com.tencent.bkrepo.auth.pojo.proxy.ProxyStatus
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class ProxyRepository: SimpleMongoDao<TProxy>() {
    fun findByProjectIdAndName(projectId: String, name: String): TProxy? {
        val query = Query(
            Criteria.where(TProxy::projectId.name).isEqualTo(projectId)
                .and(TProxy::name.name).isEqualTo(name)
        )
        return this.findOne(query)
    }

    fun findByOption(projectId: String, option: ProxyListOption): Page<TProxy> {
        with(option) {
            val pageable = Pages.ofRequest(pageNumber, pageSize)
            val query = Query(
                Criteria.where(TProxy::projectId.name).isEqualTo(projectId)
                    .apply {
                        name?.let { and(TProxy::name.name).isEqualTo(name) }
                        displayName?.let { and(TProxy::displayName.name).isEqualTo(displayName) }
                    }
            )
            val total = count(query)
            val data = find(query.with(pageable))
            return PageImpl(data, pageable, total)
        }
    }

    fun findStatusNotOffline(): List<TProxy> {
        val query = Query(
            Criteria.where(TProxy::status.name).ne(ProxyStatus.OFFLINE)
        )
        return find(query)
    }

    fun deleteByProjectIdAndName(projectId: String, name: String) {
        val query = Query(
            Criteria.where(TProxy::projectId.name).isEqualTo(projectId)
                .and(TProxy::name.name).isEqualTo(name)
        )
        this.remove(query)
    }
}
