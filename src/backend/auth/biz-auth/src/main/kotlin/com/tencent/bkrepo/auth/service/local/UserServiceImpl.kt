/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.helper.UserHelper
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.pojo.token.Token
import com.tencent.bkrepo.auth.pojo.token.TokenResult
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToProjectRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToRepoRequest
import com.tencent.bkrepo.auth.pojo.user.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.user.User
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.auth.util.DataDigestUtils
import com.tencent.bkrepo.auth.util.request.UserRequestUtil
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.util.DesensitizedUtils
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class UserServiceImpl constructor(
    private val roleRepository: RoleRepository,
    private val userDao: UserDao
) : UserService {

    @Autowired
    lateinit var repositoryService: RepositoryService

    @Autowired
    lateinit var projectService: ProjectService

    @Autowired
    lateinit var devopsAuthConfig: DevopsAuthConfig

    @Autowired
    lateinit var bkAuthService: BkAuthService

    private val userHelper by lazy { UserHelper(userDao, roleRepository) }

    override fun createUser(request: CreateUserRequest): Boolean {
        // todo 校验
        logger.info("create user request : [${DesensitizedUtils.toString(request)}]")
        // create a anonymous user is not allowed
        if (request.userId == ANONYMOUS_USER) {
            logger.warn("create user [${request.userId}]  is exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_UID)
        }
        val user = userDao.findFirstByUserId(request.userId)
        user?.let {
            logger.warn("create user [${request.userId}]  is exist.")
            return true
        }
        if (request.group && request.asstUsers.isEmpty()) {
            throw ErrorCodeException(AuthMessageCode.AUTH_ASST_USER_EMPTY)
        }
        // check asstUsers
        request.asstUsers.forEach {
            val asstUser = userDao.findFirstByUserId(it)
            if (asstUser != null && asstUser.group) {
                throw ErrorCodeException(AuthMessageCode.AUTH_ENTITY_USER_NOT_EXIST)
            } else if (asstUser != null && !asstUser.group) {
                return@forEach
            }
            val createRequest = CreateUserRequest(userId = it, name = it)
            createUser(createRequest)
        }
        val hashPwd = if (request.pwd == null) {
            userHelper.randomPassWord()
        } else {
            DataDigestUtils.md5FromStr(request.pwd!!)
        }
        val tenantIdFromHeader = SecurityUtils.getTenantId()
        val tenantId = if (tenantIdFromHeader.isNullOrEmpty()) request.tenantId else tenantIdFromHeader
        val userRequest = UserRequestUtil.convToTUser(request, hashPwd, tenantId)
        try {
            userDao.insert(userRequest)
        } catch (ignore: DuplicateKeyException) {
        }

        return true
    }

    override fun createUserToRepo(request: CreateUserToRepoRequest): Boolean {
        logger.info("create user to repo request : [${DesensitizedUtils.toString(request)}]")
        repositoryService.getRepoInfo(request.projectId, request.repoName) ?: run {
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
        projectService.getProjectInfo(request.projectId) ?: run {
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

    override fun listUser(rids: List<String>, tenantId: String?): List<User> {
        logger.debug("list user rids : [$rids]")
        return if (rids.isEmpty()) {
            // 排除被锁定的用户
            userDao.getUserNotLocked(tenantId).map { UserRequestUtil.convToUser(it) }
        } else {
            userDao.findAllByRolesIn(rids).map { UserRequestUtil.convToUser(it) }
        }
    }

    override fun deleteById(userId: String): Boolean {
        logger.info("delete user userId : [$userId]")
        userHelper.checkUserExist(userId)
        return userDao.removeByUserId(userId)
    }

    override fun addUserToRole(userId: String, roleId: String): User? {
        logger.info("add user to role userId : [$userId, $roleId]")
        // check user
        userHelper.checkUserExist(userId)
        // check role
        userHelper.checkRoleExist(roleId)
        // check is role bind to role
        if (!userHelper.checkUserRoleBind(userId, roleId)) {
            userDao.addUserToRole(userId, roleId)
        }
        return getUserById(userId)
    }

    override fun addUserToRoleBatch(idList: List<String>, roleId: String): Boolean {
        logger.info("delete user to role batch : [$idList, $roleId]")
        return userHelper.addUserToRoleBatchCommon(idList, roleId)
    }

    override fun removeUserFromRole(userId: String, roleId: String): User? {
        logger.info("remove user from role userId : [$userId], roleId : [$roleId]")
        // check user
        userHelper.checkUserExist(userId)
        // check role
        userHelper.checkRoleExist(roleId)
        userDao.removeUserFromRole(userId, roleId)
        return getUserById(userId)
    }

    override fun removeUserFromRoleBatch(idList: List<String>, roleId: String): Boolean {
        logger.info("remove user from role batch : [$idList, $roleId]")
        return userHelper.removeUserFromRoleBatchCommon(idList, roleId)
    }

    override fun updateUserById(userId: String, request: UpdateUserRequest): Boolean {
        logger.info("update user userId : [$userId], request : [$request]")
        userHelper.checkUserExist(userId)
        return userDao.updateUserById(userId, request)
    }

    override fun createToken(userId: String): Token? {
        logger.info("create token userId : [$userId]")
        val token = UserRequestUtil.generateToken()
        return addUserToken(userId, token, null)
    }

    override fun createOrUpdateUser(userId: String, name: String, tenantId: String?) {
        logger.info("create or update user : [$userId,$name,$tenantId]")
        if (!userHelper.isUserExist(userId)) {
            val userName = name.ifEmpty { userId }
            val createRequest = CreateUserRequest(userId = userId, name = userName, tenantId = tenantId)
            createUser(createRequest)
        } else {
            val updateUserRequest = UpdateUserRequest(name = userId)
            if (name.isNotEmpty()) {
                updateUserRequest.name = name
            }
            tenantId?.let {
                updateUserRequest.tenantId = tenantId
            }
            updateUserById(userId, updateUserRequest)
        }
    }

    override fun addUserToken(userId: String, name: String, expiredAt: String?): Token? {
        try {
            logger.info("add user token userId : [$userId] ,token : [$name]")
            userHelper.checkUserExist(userId)

            val existUserInfo = userDao.findFirstByUserId(userId)
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
            userDao.addUserToken(userId, dataToken)
            val userInfo = userDao.findFirstByUserId(userId)
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
        logger.debug("list user token : [$userId]")
        userHelper.checkUserExist(userId)
        return UserRequestUtil.convTokenResult(userDao.findFirstByUserId(userId)!!.tokens)
    }

    override fun listValidToken(userId: String): List<Token> {
        logger.debug("list valid token : [$userId]")
        userHelper.checkUserExist(userId)
        return userDao.findFirstByUserId(userId)!!.tokens.filter {
            it.expiredAt == null || it.expiredAt!!.isAfter(LocalDateTime.now())
        }
    }

    override fun removeToken(userId: String, name: String): Boolean {
        logger.info("remove token userId : [$userId] ,name : [$name]")
        userHelper.checkUserExist(userId)
        userDao.removeTokenFromUser(userId, name)
        return true
    }

    override fun getUserById(userId: String): User? {
        logger.debug("get user userId : [$userId]")
        val user = userDao.findFirstByUserId(userId) ?: return null
        return UserRequestUtil.convToUser(user)
    }

    override fun getUserInfoByIdAndTenantId(userId: String, tenantId: String?): UserInfo? {
        logger.debug("get user userId : [$userId, $tenantId]")
        var user = userDao.findFirstByUserId(userId)
        if (user == null && tenantId != null) {
            if (bkAuthService.checkBkUserExist(userId, tenantId)) {
                val createRequest = CreateUserRequest(userId = userId, name = userId, tenantId = tenantId)
                createUser(createRequest)
            }
            user = userDao.findFirstByUserId(userId)
        }
        if (user == null) return null
        return UserRequestUtil.convToUserInfo(user)
    }

    override fun findUserByUserToken(userId: String, pwd: String): User? {
        logger.debug("find user userId : [$userId]")
        if (pwd == DEFAULT_PASSWORD && !devopsAuthConfig.allowDefaultPwd) {
            logger.warn("login with default password [$userId]")
            if (!devopsAuthConfig.userIdSet.split(",").contains(userId)) {
                logger.warn("login with default password not in list[$userId]")
                return null
            }
        }
        val hashPwd = DataDigestUtils.md5FromStr(pwd)
        val sm3HashPwd = DataDigestUtils.sm3FromStr(pwd)
        val result = userDao.getUserByPassWordAndHash(userId, pwd, hashPwd, sm3HashPwd) ?: return null
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
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = userDao.countByName(userName, admin, locked)
        val records = userDao.getByPage(userName, admin, locked, pageNumber, pageSize).map {
            UserRequestUtil.convToUserInfo(it)
        }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun getUserInfoById(userId: String): UserInfo? {
        val tUser = userDao.findFirstByUserId(userId) ?: return null
        return UserRequestUtil.convToUserInfo(tUser)
    }

    override fun getUserInfoByToken(token: String): UserInfo? {
        val tUser = userDao.findFirstByToken(token) ?: return null
        return UserRequestUtil.convToUserInfo(tUser)
    }

    override fun getUserPwdById(userId: String): String? {
        val tUser = userDao.findFirstByUserId(userId) ?: return null
        return tUser.pwd
    }

    override fun updatePassword(userId: String, oldPwd: String, newPwd: String): Boolean {
        logger.info("update user password : [$userId]")
        val hashOldPwd = DataDigestUtils.md5FromStr(oldPwd)
        val hashNewPwd = DataDigestUtils.md5FromStr(newPwd)
        val user = userDao.getByUserIdAndPassword(userId, hashOldPwd)
        user?.let {
            return userDao.updatePasswordByUserId(userId, hashNewPwd)
        }
        throw ErrorCodeException(CommonMessageCode.MODIFY_PASSWORD_FAILED, "modify password failed!")
    }

    override fun resetPassword(userId: String): Boolean {
        // todo 鉴权
        val newHashPwd = DataDigestUtils.md5FromStr(DEFAULT_PASSWORD)
        return userDao.updatePasswordByUserId(userId, newHashPwd)
    }

    override fun repeatUid(userId: String): Boolean {
        val user = userDao.findFirstByUserId(userId)
        return user != null
    }

    override fun addUserAccount(userId: String, accountId: String): Boolean {
        userHelper.checkUserExist(userId)
        return userDao.addUserAccount(userId, accountId)
    }

    override fun removeUserAccount(userId: String, accountId: String): Boolean {
        userHelper.checkUserExist(userId)
        return userDao.removeUserAccount(userId, accountId)
    }

    override fun validateEntityUser(userId: String): Boolean {
        val user = userDao.findFirstByUserId(userId)
        return user != null && !user.group
    }

    override fun getRelatedUserById(userId: String): List<UserInfo> {
        return userDao.getUserByAsstUser(userId).map { UserRequestUtil.convToUserInfo(it) }
    }

    override fun listAdminUsers(): List<String> {
        return userDao.findAllAdminUsers().map { it.userId }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserServiceImpl::class.java)
    }
}

