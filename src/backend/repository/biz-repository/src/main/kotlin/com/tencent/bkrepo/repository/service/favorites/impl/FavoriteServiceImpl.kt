/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.service.favorites.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.repository.dao.FavoriteDao
import com.tencent.bkrepo.repository.model.TFavorites
import com.tencent.bkrepo.repository.pojo.favorite.FavoriteCreateRequest
import com.tencent.bkrepo.repository.pojo.favorite.FavoriteQueryRequest
import com.tencent.bkrepo.repository.pojo.favorite.FavoriteResult
import com.tencent.bkrepo.repository.pojo.favorite.FavoriteType
import com.tencent.bkrepo.repository.service.favorites.FavoriteService
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

@Service
class FavoriteServiceImpl(
    private val favoriteDao: FavoriteDao,
) : FavoriteService {

    override fun createFavorite(request: FavoriteCreateRequest) {
        val favorite = TFavorites(
            id = null,
            path = request.path,
            repoName = request.repoName,
            projectId = request.projectId,
            userId = request.userId,
            createdDate = request.createdDate,
            type = request.type
        )
        try {
            favoriteDao.insert(favorite)
        } catch (exception: DuplicateKeyException) {
            logger.warn("invalid params $request")
        }

    }

    override fun queryFavorite(userId: String, request: FavoriteQueryRequest): Page<FavoriteResult> {
        with(request) {
            val query = Query()
            if (type == FavoriteType.PROJECT) {
                query.addCriteria(
                    Criteria.where(TFavorites::type.name).`is`(FavoriteType.PROJECT).and(TFavorites::projectId.name)
                        .`is`(projectId)
                )
            } else {
                query.addCriteria(
                    Criteria.where(TFavorites::type.name).`is`(FavoriteType.USER).and(TFavorites::projectId.name)
                        .`is`(projectId).and(TFavorites::userId.name).`is`(userId)
                )
            }
            val records =
                favoriteDao.find(query).map { FavoriteResult(it.id, it.projectId, it.repoName, it.path, it.type) }
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val totalRecords = favoriteDao.count(query)
            return Pages.ofResponse(pageRequest, totalRecords, records)
        }
    }

    override fun removeFavorite(id: String) {
        favoriteDao.remove(Query.query(Criteria.where("_id").`is`(id)))
    }

    override fun getFavoriteById(id: String): TFavorites? {
        return favoriteDao.findById(id)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FavoriteServiceImpl::class.java)
    }

}