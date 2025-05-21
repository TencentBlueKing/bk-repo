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

package com.tencent.bkrepo.auth.helper


import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.pojo.role.CreateRoleRequest
import com.tencent.bkrepo.auth.util.DataDigestUtils
import com.tencent.bkrepo.auth.util.IDUtil
import com.tencent.bkrepo.auth.util.request.RoleRequestUtil
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.TENANT_ID
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.slf4j.LoggerFactory

class UserHelper constructor(
    private val userDao: UserDao,
    private val roleRepository: RoleRepository
) {

    fun checkUserExist(userId: String) {
        userDao.findFirstByUserId(userId) ?: run {
            logger.warn("user [$userId] not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
    }

    fun isUserExist(userId: String): Boolean {
        return userDao.findFirstByUserId(userId) != null
    }

    fun getTenantId(): String? {
        return HttpContextHolder.getRequestOrNull()?.getHeader(TENANT_ID)
    }

    fun checkUserRoleBind(userId: String, roleId: String): Boolean {
        userDao.findFirstByUserIdAndRoles(userId, roleId) ?: run {
            logger.warn("user [$userId,$roleId]  not exist.")
            return false
        }
        return true
    }

    // check user is existed
    private fun checkUserExistBatch(idList: List<String>) {
        idList.forEach {
            userDao.findFirstByUserId(it) ?: run {
                logger.warn("user not  exist.")
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
            }
        }
    }

    private fun checkOrCreateUser(userIdList: List<String>) {
        userIdList.forEach {
            userDao.findFirstByUserId(it) ?: run {
                if (it != ANONYMOUS_USER) {
                    val user = TUser(
                        userId = it,
                        name = it,
                        pwd = randomPassWord()
                    )
                    userDao.insert(user)
                }
            }
        }
    }

    fun randomPassWord(): String {
        return DataDigestUtils.md5FromStr(IDUtil.genRandomId())
    }

    // check role is existed
    fun checkRoleExist(roleId: String) {
        val role = roleRepository.findFirstById(roleId)
        role ?: run {
            logger.warn("role not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        }
    }

    fun createRoleCommon(request: CreateRoleRequest): String? {
        logger.info("create role request:[$request] ")
        val role = when (request.type) {
            RoleType.REPO -> {
                require(request.roleId != null)
                roleRepository.findFirstByTypeAndRoleIdAndProjectIdAndRepoName(
                    type = RoleType.REPO,
                    roleId = request.roleId,
                    projectId = request.projectId,
                    repoName = request.repoName!!
                )
            }
            RoleType.PROJECT -> {
                if (request.source == null) {
                    roleRepository.findFirstByTypeAndProjectIdAndName(
                        type = RoleType.PROJECT,
                        projectId = request.projectId,
                        name = request.name
                    )
                } else {
                    require(request.roleId != null)
                    roleRepository.findFirstByTypeAndRoleIdAndProjectIdAndSource(
                        type = RoleType.PROJECT,
                        roleId = request.roleId,
                        projectId = request.projectId,
                        source = request.source
                    )
                }
            }
        }

        role?.let {
            logger.warn("create role [${request.roleId} , ${request.projectId} ] is exist.")
            return role.id
        }

        val roleId = when (request.type) {
            RoleType.REPO -> request.roleId!!
            RoleType.PROJECT -> findUsableProjectTypeRoleId(request.roleId, request.projectId)
        }

        val result = roleRepository.insert(RoleRequestUtil.conv2TRole(roleId, request))
        return result.id
    }

    fun addUserToRoleBatchCommon(userIdList: List<String>, roleId: String): Boolean {
        logger.info("add user to role batch userId : [$userIdList], roleId : [$roleId]")
        checkOrCreateUser(userIdList)
        checkRoleExist(roleId)
        userDao.addRoleToUsers(userIdList, roleId)
        return true
    }

    fun removeUserFromRoleBatchCommon(userIdList: List<String>, roleId: String): Boolean {
        logger.info("remove user from role batch userId : [$userIdList], roleId : [$roleId]")
        checkUserExistBatch(userIdList)
        checkRoleExist(roleId)
        userDao.removeRoleFromUsers(userIdList, roleId)
        return true
    }

    private fun findUsableProjectTypeRoleId(roleId: String?, projectId: String): String {
        var tempRoleId = roleId ?: buildProjectRoleId(projectId)
        while (true) {
            val role = roleRepository.findFirstByRoleIdAndProjectId(tempRoleId, projectId)
            if (role == null) return tempRoleId else tempRoleId = buildProjectRoleId(projectId)
        }
    }

    private fun buildProjectRoleId(projectId: String): String {
        return "${projectId}_role_${IDUtil.shortUUID()}"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserHelper::class.java)
    }
}
