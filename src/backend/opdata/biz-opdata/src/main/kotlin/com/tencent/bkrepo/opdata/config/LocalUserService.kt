/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
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

package com.tencent.bkrepo.opdata.config

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.security.spi.UserAuthProvider
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * opdata 模块的本地用户查询服务。
 *
 * 直接通过 [MongoTemplate] 查询 auth 模块共享的 `user` 集合获取用户信息，
 * 绕过对 auth 微服务的 Feign 调用，使 opdata 在 auth 微服务不可用时仍能正常进行：
 * 1. `@Principal(ADMIN)` 鉴权判定（[isAdmin]）；
 * 2. 前端获取用户信息（[getUserInfoById]、[findUser]）。
 *
 * 通过本地缓存减少数据库压力；查询失败时 admin 判定保守返回 false（拒绝访问）。
 *
 * 注意：本服务仅做只读查询，用户创建/更新等写操作仍由 auth 微服务负责，
 * 避免多服务并发写入 user 集合导致数据一致性问题。
 */
class LocalUserService(
    private val mongoTemplate: MongoTemplate
) : UserAuthProvider {

    /**
     * admin 标志缓存，miss 时查询 mongo。
     */
    private val adminCache: LoadingCache<String, Boolean> = CacheBuilder.newBuilder()
        .maximumSize(MAX_CACHE_SIZE)
        .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
        .build(CacheLoader.from { userId -> loadAdminFromDb(userId!!) })

    /**
     * 用户信息缓存，使用 Optional 以支持缓存空值（用户不存在场景）。
     */
    private val userCache: LoadingCache<String, Optional<UserRecord>> = CacheBuilder.newBuilder()
        .maximumSize(MAX_CACHE_SIZE)
        .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
        .build(CacheLoader.from { userId -> Optional.ofNullable(loadUserFromDb(userId!!)) })

    override fun isAdmin(userId: String): Boolean {
        if (userId.isEmpty()) {
            return false
        }
        return try {
            adminCache.get(userId)
        } catch (e: Exception) {
            logger.warn("load admin flag for [$userId] failed, deny by default", e)
            false
        }
    }

    /**
     * 按 userId 查询用户信息，不存在返回 null。
     */
    fun findUser(userId: String): UserRecord? {
        if (userId.isEmpty()) {
            return null
        }
        return try {
            userCache.get(userId).orElse(null)
        } catch (e: Exception) {
            logger.warn("load user info for [$userId] failed", e)
            null
        }
    }

    private fun loadAdminFromDb(userId: String): Boolean {
        return try {
            val query = Query(Criteria.where(FIELD_USER_ID).`is`(userId))
            query.fields().include(FIELD_ADMIN)
            val doc = mongoTemplate.findOne(query, Map::class.java, COLLECTION_USER)
            doc?.get(FIELD_ADMIN) as? Boolean ?: false
        } catch (e: Exception) {
            logger.warn("query user[$userId] admin flag from mongo failed", e)
            false
        }
    }

    private fun loadUserFromDb(userId: String): UserRecord? {
        return try {
            val query = Query(Criteria.where(FIELD_USER_ID).`is`(userId))
            query.fields()
                .include(FIELD_USER_ID)
                .include(FIELD_NAME)
                .include(FIELD_ADMIN)
                .include(FIELD_LOCKED)
                .include(FIELD_GROUP)
                .include(FIELD_EMAIL)
                .include(FIELD_PHONE)
                .include(FIELD_ASST_USERS)
                .include(FIELD_TENANT_ID)
                .include(FIELD_CREATED_DATE)
            val doc = mongoTemplate.findOne(query, Map::class.java, COLLECTION_USER) ?: return null
            @Suppress("UNCHECKED_CAST")
            UserRecord(
                userId = doc[FIELD_USER_ID] as? String ?: userId,
                name = doc[FIELD_NAME] as? String ?: userId,
                admin = doc[FIELD_ADMIN] as? Boolean ?: false,
                locked = doc[FIELD_LOCKED] as? Boolean ?: false,
                group = doc[FIELD_GROUP] as? Boolean ?: false,
                email = doc[FIELD_EMAIL] as? String,
                phone = doc[FIELD_PHONE] as? String,
                asstUsers = (doc[FIELD_ASST_USERS] as? List<String>) ?: emptyList(),
                tenantId = doc[FIELD_TENANT_ID] as? String,
                createdDate = doc[FIELD_CREATED_DATE] as? LocalDateTime
            )
        } catch (e: Exception) {
            logger.warn("query user[$userId] info from mongo failed", e)
            null
        }
    }

    /**
     * 从 mongo user 集合读取到的用户信息快照，只包含前端需要的字段。
     */
    data class UserRecord(
        val userId: String,
        val name: String,
        val admin: Boolean,
        val locked: Boolean,
        val group: Boolean,
        val email: String?,
        val phone: String?,
        val asstUsers: List<String>,
        val tenantId: String?,
        val createdDate: LocalDateTime?
    )

    companion object {
        private val logger = LoggerFactory.getLogger(LocalUserService::class.java)
        private const val COLLECTION_USER = "user"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_NAME = "name"
        private const val FIELD_ADMIN = "admin"
        private const val FIELD_LOCKED = "locked"
        private const val FIELD_GROUP = "group"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_PHONE = "phone"
        private const val FIELD_ASST_USERS = "asstUsers"
        private const val FIELD_TENANT_ID = "tenantId"
        private const val FIELD_CREATED_DATE = "createdDate"
        private const val MAX_CACHE_SIZE = 10_000L
        private const val CACHE_EXPIRE_MINUTES = 5L
    }
}
