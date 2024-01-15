/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.service.local

import com.tencent.bkrepo.auth.config.DevopsAuthConfig
import com.tencent.bkrepo.auth.constant.DEFAULT_PASSWORD
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.token.Token
import com.tencent.bkrepo.auth.pojo.token.TokenResult
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToProjectRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToRepoRequest
import com.tencent.bkrepo.auth.pojo.user.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.user.User
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.auth.util.DataDigestUtils
import com.tencent.bkrepo.auth.util.IDUtil
import com.tencent.bkrepo.auth.util.query.UserQueryHelper
import com.tencent.bkrepo.auth.util.query.UserUpdateHelper
import com.tencent.bkrepo.auth.util.request.UserRequestUtil
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.operate.service.util.DesensitizedUtils
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class UserServiceImpl constructor(
    private val userRepository: UserRepository,
    roleRepository: RoleRepository,
    private val mongoTemplate: MongoTemplate
) : UserService, AbstractServiceImpl(mongoTemplate, userRepository, roleRepository) {

    @Autowired
    lateinit var repoClient: RepositoryClient

    @Autowired
    lateinit var projectClient: ProjectClient

    @Autowired
    lateinit var bkAuthConfig: DevopsAuthConfig

    override fun createUser(request: CreateUserRequest): Boolean {
        // todo 校验
        logger.info("create user request : [${DesensitizedUtils.toString(request)}]")
        // create a anonymous user is not allowed
        if (request.userId == ANONYMOUS_USER) {
            logger.warn("create user [${request.userId}]  is exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_UID)
        }
        val user = userRepository.findFirstByUserId(request.userId)
        user?.let {
            logger.warn("create user [${request.userId}]  is exist.")
            return true
        }
        if (request.group && request.asstUsers.isEmpty()) {
            throw ErrorCodeException(AuthMessageCode.AUTH_ASST_USER_EMPTY)
        }
        // check asstUsers
        request.asstUsers.forEach {
            val asstUser = userRepository.findFirstByUserId(it)
            if (asstUser != null && asstUser.group) {
                throw ErrorCodeException(AuthMessageCode.AUTH_ENTITY_USER_NOT_EXIST)
            } else if (asstUser != null && !asstUser.group) {
                return@forEach
            }
            val createRequest = CreateUserRequest(userId = it, name = it)
            createUser(createRequest)
        }
        val hashPwd = if (request.pwd == null) {
            DataDigestUtils.md5FromStr(IDUtil.genRandomId())
        } else {
            DataDigestUtils.md5FromStr(request.pwd!!)
        }
        val userRequest = UserRequestUtil.convToTUser(request, hashPwd)
        try {
            userRepository.insert(userRequest)
        } catch (ignore: DuplicateKeyException) {
        }

        return true
    }

    override fun createUserToRepo(request: CreateUserToRepoRequest): Boolean {
        logger.info("create user to repo request : [${DesensitizedUtils.toString(request)}]")
        repoClient.getRepoInfo(request.projectId, request.repoName).data ?: run {
            logger.warn("repo [${request.projectId}/${request.repoName}]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_REPO_NOT_EXIST)
        }
        // user not exist, create user
        try {
            val userResult = createUser(UserRequestUtil.convToCreateRepoUserRequest(request))
            if (!userResult) {
                logger.warn("create user fail [$request]")
                return false
            }
            request.pwd?.let {
                val updateRequest = UpdateUserRequest(pwd = request.pwd)
                updateUserById(request.userId, updateRequest)
            }
        } catch (exception: ErrorCodeException) {
            if (exception.messageCode == AuthMessageCode.AUTH_DUP_UID) {
                return true
            }
            throw exception
        }
        return true
    }

    override fun createUserToProject(request: CreateUserToProjectRequest): Boolean {
        logger.info("create user to project request : [${DesensitizedUtils.toString(request)}]")
        projectClient.getProjectInfo(request.projectId).data ?: run {
            logger.warn("project [${request.projectId}]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_PROJECT_NOT_EXIST)
        }
        // user not exist, create user
        try {
            val userResult = createUser(UserRequestUtil.convToCreateProjectUserRequest(request))
            if (!userResult) {
                logger.warn("create user fail [$request]")
                return false
            }
            request.pwd?.let {
                val updateRequest = UpdateUserRequest(pwd = request.pwd)
                updateUserById(request.userId, updateRequest)
            }
        } catch (exception: ErrorCodeException) {
            if (exception.messageCode == AuthMessageCode.AUTH_DUP_UID) {
                return true
            }
            throw exception
        }
        return true
    }

    override fun listUser(rids: List<String>): List<User> {
        logger.debug("list user rids : [$rids]")
        return if (rids.isEmpty()) {
            // 排除被锁定的用户
            val filter = UserQueryHelper.filterNotLockedUser()
            mongoTemplate.find(filter, TUser::class.java).map { UserRequestUtil.convToUser(it) }
        } else {
            userRepository.findAllByRolesIn(rids).map { UserRequestUtil.convToUser(it) }
        }
    }

    override fun deleteById(userId: String): Boolean {
        logger.info("delete user userId : [$userId]")
        checkUserExist(userId)
        userRepository.deleteByUserId(userId)
        return true
    }

    override fun addUserToRole(userId: String, roleId: String): User? {
        logger.info("add user to role userId : [$userId], roleId : [$roleId]")
        // check user
        checkUserExist(userId)
        // check role
        checkRoleExist(roleId)
        // check is role bind to role
        if (!checkUserRoleBind(userId, roleId)) {
            val query = UserQueryHelper.getUserById(userId)
            val update = Update()
            update.push(TUser::roles.name, roleId)
            mongoTemplate.upsert(query, update, TUser::class.java)
        }
        return getUserById(userId)
    }

    override fun addUserToRoleBatch(idList: List<String>, roleId: String): Boolean {
        return addUserToRoleBatchCommon(idList, roleId)
    }

    override fun removeUserFromRole(userId: String, roleId: String): User? {
        logger.info("remove user from role userId : [$userId], roleId : [$roleId]")
        // check user
        checkUserExist(userId)
        // check role
        checkRoleExist(roleId)
        val query = UserQueryHelper.getUserByIdAndRoleId(userId, roleId)
        val update = UserUpdateHelper.buildUnsetRoles()
        mongoTemplate.upsert(query, update, TUser::class.java)
        return getUserById(userId)
    }

    override fun removeUserFromRoleBatch(idList: List<String>, roleId: String): Boolean {
        return removeUserFromRoleBatchCommon(idList, roleId)
    }

    override fun updateUserById(userId: String, request: UpdateUserRequest): Boolean {
        logger.info("update user userId : [$userId], request : [$request]")
        checkUserExist(userId)

        val query = UserQueryHelper.getUserById(userId)
        val update = UserUpdateHelper.buildUpdateUser(request)
        val result = mongoTemplate.updateFirst(query, update, TUser::class.java)
        if (result.modifiedCount == 1L) return true
        return false
    }

    override fun createToken(userId: String): Token? {
        logger.info("create token userId : [$userId]")
        val token = UserRequestUtil.generateToken()
        return addUserToken(userId, token, null)
    }

    override fun addUserToken(userId: String, name: String, expiredAt: String?): Token? {
        try {
            logger.info("add user token userId : [$userId] ,token : [$name]")
            checkUserExist(userId)

            val existUserInfo = userRepository.findFirstByUserId(userId)
            val existTokens = existUserInfo!!.tokens
            var id = UserRequestUtil.generateToken()
            var createdTime = LocalDateTime.now()
            existTokens.forEach {
                // 如果临时token已经存在，尝试更新token的过期时间
                if (it.name == name && it.expiredAt != null) {
                    // 先删除token
                    removeToken(userId, name)
                    id = it.id
                    createdTime = it.createdAt
                } else if (it.name == name && it.expiredAt == null) {
                    logger.warn("user token exist [$name]")
                    throw ErrorCodeException(AuthMessageCode.AUTH_USER_TOKEN_EXIST)
                }
            }
            // 创建token
            val query = UserQueryHelper.getUserById(userId)
            val update = Update()
            var expiredTime: LocalDateTime? = null
            if (expiredAt != null && expiredAt.isNotEmpty()) {
                val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                expiredTime = LocalDateTime.parse(expiredAt, dateTimeFormatter)
                // conv time
                expiredTime = expiredTime!!.plusHours(8)
            }
            val sm3Id = DataDigestUtils.sm3FromStr(id)
            val userToken = Token(name = name, id = id, createdAt = createdTime, expiredAt = expiredTime)
            val dataToken = Token(name = name, id = sm3Id, createdAt = createdTime, expiredAt = expiredTime)
            update.addToSet(TUser::tokens.name, dataToken)
            mongoTemplate.upsert(query, update, TUser::class.java)
            val userInfo = userRepository.findFirstByUserId(userId)
            val tokens = userInfo!!.tokens
            tokens.forEach {
                if (it.name == name) return userToken
            }
            return null
        } catch (ignored: DateTimeParseException) {
            logger.error("add user token false [$ignored]")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_TOKEN_TIME_ERROR)
        }
    }

    override fun listUserToken(userId: String): List<TokenResult> {
        checkUserExist(userId)
        return UserRequestUtil.convTokenResult(userRepository.findFirstByUserId(userId)!!.tokens)
    }

    override fun listValidToken(userId: String): List<Token> {
        checkUserExist(userId)
        return userRepository.findFirstByUserId(userId)!!.tokens.filter {
            it.expiredAt == null || it.expiredAt!!.isAfter(LocalDateTime.now())
        }
    }

    override fun removeToken(userId: String, name: String): Boolean {
        logger.info("remove token userId : [$userId] ,name : [$name]")
        checkUserExist(userId)
        val query = UserQueryHelper.getUserById(userId)
        val update = UserUpdateHelper.buildUnsetTokenName(name)
        mongoTemplate.updateFirst(query, update, TUser::class.java)
        return true
    }

    override fun getUserById(userId: String): User? {
        logger.debug("get user userId : [$userId]")
        val user = userRepository.findFirstByUserId(userId) ?: return null
        return UserRequestUtil.convToUser(user)
    }

    override fun findUserByUserToken(userId: String, pwd: String): User? {
        logger.debug("find user userId : [$userId]")
        if (pwd == DEFAULT_PASSWORD && !bkAuthConfig.allowDefaultPwd) {
            logger.warn("login with default password [$userId]")
            if (!bkAuthConfig.userIdSet.split(",").contains(userId)) {
                logger.warn("login with default password not in list[$userId]")
                return null
            }
        }
        logger.debug("find user userId : [$userId]")
        val hashPwd = DataDigestUtils.md5FromStr(pwd)
        val sm3HashPwd = DataDigestUtils.sm3FromStr(pwd)
        val query = UserQueryHelper.buildUserPasswordCheck(userId, pwd, hashPwd, sm3HashPwd)
        val result = mongoTemplate.findOne(query, TUser::class.java) ?: run {
            return null
        }
        // password 匹配成功，返回
        if (result.pwd == hashPwd) {
            return UserRequestUtil.convToUser(result)
        }

        // token 匹配成功
        result.tokens.forEach {
            // 永久token，校验通过，临时token校验有效期
            if (UserRequestUtil.matchToken(pwd, sm3HashPwd, it.id) && it.expiredAt == null) {
                return UserRequestUtil.convToUser(result)
            } else if (UserRequestUtil.matchToken(pwd, sm3HashPwd, it.id) &&
                it.expiredAt != null && it.expiredAt!!.isAfter(LocalDateTime.now())
            ) {
                return UserRequestUtil.convToUser(result)
            }
        }

        return null
    }

    override fun userPage(
        pageNumber: Int, pageSize: Int, userName: String?, admin: Boolean?, locked: Boolean?
    ): Page<UserInfo> {
        val query = UserQueryHelper.getUserByName(userName, admin, locked)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = mongoTemplate.count(query, TUser::class.java)
        val records = mongoTemplate.find(query.with(pageRequest), TUser::class.java).map {
            UserRequestUtil.convToUserInfo(it)
        }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun getUserInfoById(userId: String): UserInfo? {
        val tUser = userRepository.findFirstByUserId(userId) ?: return null
        return UserRequestUtil.convToUserInfo(tUser)
    }

    override fun getUserPwdById(userId: String): String? {
        val tUser = userRepository.findFirstByUserId(userId) ?: return null
        return tUser.pwd
    }

    override fun updatePassword(userId: String, oldPwd: String, newPwd: String): Boolean {
        val query = UserQueryHelper.getUserByIdAndPwd(userId, oldPwd)
        val user = mongoTemplate.find(query, TUser::class.java)
        if (user.isNotEmpty()) {
            val updateQuery = UserQueryHelper.getUserById(userId)
            val update = UserUpdateHelper.buildPwdUpdate(newPwd)
            val record = mongoTemplate.updateFirst(updateQuery, update, TUser::class.java)
            if (record.modifiedCount == 1L || record.matchedCount == 1L) return true
        }
        throw ErrorCodeException(CommonMessageCode.MODIFY_PASSWORD_FAILED, "modify password failed!")
    }

    override fun resetPassword(userId: String): Boolean {
        // todo 鉴权
        val query = UserQueryHelper.getUserById(userId)
        val update = Update().set(TUser::pwd.name, DataDigestUtils.md5FromStr(DEFAULT_PASSWORD))
        val record = mongoTemplate.updateFirst(query, update, TUser::class.java)
        if (record.modifiedCount == 1L || record.matchedCount == 1L) return true
        return false
    }

    override fun repeatUid(userId: String): Boolean {
        val query = UserQueryHelper.getUserById(userId)
        val record = mongoTemplate.find(query, TUser::class.java)
        return record.isNotEmpty()
    }

    override fun addUserAccount(userId: String, accountId: String): Boolean {
        checkUserExist(userId)
        val query = Query(Criteria(TUser::userId.name).isEqualTo(userId))
        val update = Update().addToSet(TUser::accounts.name, accountId)
        val record = mongoTemplate.updateFirst(query, update, TUser::class.java)
        if (record.modifiedCount == 1L || record.matchedCount == 1L) return true
        return false
    }

    override fun removeUserAccount(userId: String, accountId: String): Boolean {
        checkUserExist(userId)
        val query = Query(Criteria(TUser::userId.name).isEqualTo(userId))
        val update = Update().pull(TUser::accounts.name, accountId)
        val record = mongoTemplate.updateFirst(query, update, TUser::class.java)
        if (record.modifiedCount == 1L || record.matchedCount == 1L) return true
        return false
    }

    override fun validateEntityUser(userId: String): Boolean {
        val query = UserQueryHelper.getUserById(userId)
        val record = mongoTemplate.findOne(query, TUser::class.java)
        return record != null && !record.group
    }

    override fun getRelatedUserById(asstUser: String, userName: String?): List<UserInfo> {
        val query = UserQueryHelper.getUserByAsstUsers(asstUser, userName)
        return mongoTemplate.find(query, TUser::class.java).map { UserRequestUtil.convToUserInfo(it) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserServiceImpl::class.java)
    }
}
