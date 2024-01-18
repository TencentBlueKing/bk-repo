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

import com.mongodb.BasicDBObject
import com.tencent.bkrepo.auth.constant.PROJECT_VIEWER_ID
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.account.ScopeRule
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.pojo.permission.PermissionSet
import com.tencent.bkrepo.auth.pojo.role.CreateRoleRequest
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.util.DataDigestUtils
import com.tencent.bkrepo.auth.util.IDUtil
import com.tencent.bkrepo.auth.util.RequestUtil
import com.tencent.bkrepo.auth.util.query.UserQueryHelper
import com.tencent.bkrepo.auth.util.query.UserUpdateHelper
import com.tencent.bkrepo.auth.util.request.RoleRequestUtil
import com.tencent.bkrepo.auth.util.scope.ProjectRuleUtil
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime

open class AbstractServiceImpl constructor(
    private val mongoTemplate: MongoTemplate,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository
) {

    fun checkUserExist(userId: String) {
        userRepository.findFirstByUserId(userId) ?: run {
            logger.warn("user [$userId] not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
    }

    fun checkUserRoleBind(userId: String, roleId: String): Boolean {
        userRepository.findFirstByUserIdAndRoles(userId, roleId) ?: run {
            logger.warn("user [$userId,$roleId]  not exist.")
            return false
        }
        return true
    }

    // check user is existed
    private fun checkUserExistBatch(idList: List<String>) {
        idList.forEach {
            userRepository.findFirstByUserId(it) ?: run {
                logger.warn(" user not  exist.")
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
            }
        }
    }

    private fun checkUserOrCreateUser(idList: List<String>) {
        idList.forEach {
            userRepository.findFirstByUserId(it) ?: run {
                if (it != ANONYMOUS_USER) {
                    var user = TUser(
                        userId = it,
                        name = it,
                        pwd = DataDigestUtils.md5FromStr(IDUtil.genRandomId()),
                        createdDate = LocalDateTime.now(),
                        lastModifiedDate = LocalDateTime.now()
                    )
                    userRepository.insert(user)
                }
            }
        }
    }

    // check role is existed
    fun checkRoleExist(roleId: String) {
        val role = roleRepository.findTRoleById(ObjectId(roleId))
        role ?: run {
            logger.warn("role not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        }
    }

    fun isUserLocalAdmin(userId: String): Boolean {
        val user = userRepository.findFirstByUserId(userId) ?: run {
            return false
        }
        return user.admin
    }

    fun isUserLocalProjectAdmin(userId: String, projectId: String): Boolean {
        val roleIdArray = mutableListOf<String>()
        roleRepository.findByTypeAndProjectIdAndAdmin(RoleType.PROJECT, projectId, true).forEach {
            roleIdArray.add(it.id!!)
        }
        userRepository.findFirstByUserIdAndRolesIn(userId, roleIdArray) ?: run {
            return false
        }
        return true
    }

    fun updatePermissionById(id: String, key: String, value: Any): Boolean {
        val update = Update()
        update.set(key, value)
        val result = mongoTemplate.upsert(buildIdQuery(id), update, TPermission::class.java)
        if (result.matchedCount == 1L) return true
        return false
    }

    fun updatePermissionAction(pId: String, urId: String, actions: List<PermissionAction>, filed: String): Boolean {
        val update = Update()
        val userAction = PermissionSet(id = urId, action = actions)
        update.addToSet(filed, userAction)
        val result = mongoTemplate.updateFirst(buildIdQuery(pId), update, TPermission::class.java)
        if (result.matchedCount == 1L) return true
        return false
    }

    fun removePermission(id: String, uid: String, field: String): Boolean {
        val update = Update()
        val s = BasicDBObject()
        s["_id"] = uid
        update.pull(field, s)
        val result = mongoTemplate.updateFirst(buildIdQuery(id), update, TPermission::class.java)
        if (result.modifiedCount == 1L) return true
        return false
    }

    private fun buildIdQuery(id: String): Query {
        return Query.query(Criteria.where("_id").`is`(id))
    }


    fun filterRepos(repos: List<String>, originRepoNames: List<String>): List<String> {
        (repos as MutableList).retainAll(originRepoNames)
        return repos
    }

    fun checkPlatformProject(projectId: String?, scopeDesc: List<ScopeRule>?): Boolean {
        if (scopeDesc == null || projectId == null) return false

        scopeDesc.forEach {
            when (it.field) {
                ResourceType.PROJECT.name -> {
                    if (ProjectRuleUtil.check(it, projectId)) return true
                }
                else -> return false
            }
        }
        return false
    }

    fun getProjectAdminUser(projectId: String): List<String> {
        val roleIdArray = mutableListOf<String>()
        roleRepository.findByTypeAndProjectIdAndAdmin(RoleType.PROJECT, projectId, true).forEach {
            roleIdArray.add(it.id!!)
        }
        return userRepository.findAllByRolesIn(roleIdArray).map { it.userId }.distinct()
    }

    // 获取此项目一般用户
    fun getProjectCommonUser(projectId: String): List<String> {
        val roleIdArray = mutableListOf<String>()
        val role = roleRepository.findFirstByRoleIdAndProjectId(PROJECT_VIEWER_ID, projectId)
        if (role != null) role.id?.let { roleIdArray.add(it) }
        return userRepository.findAllByRolesIn(roleIdArray).map { it.userId }.distinct()
    }

    fun createRoleCommon(request: CreateRoleRequest): String? {
        logger.info("create role request:[$request] ")
        val role: TRole? = if (request.type == RoleType.REPO) {
            roleRepository.findFirstByRoleIdAndProjectIdAndRepoName(
                request.roleId!!,
                request.projectId,
                request.repoName!!
            )
        } else {
            roleRepository.findFirstByProjectIdAndTypeAndName(
                projectId = request.projectId,
                type = RoleType.PROJECT,
                name = request.name
            )
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

    fun addProjectUserAdmin(projectId: String, idList: List<String>) {
        val createRoleRequest = RequestUtil.buildProjectAdminRequest(projectId)
        val roleId = createRoleCommon(createRoleRequest)
        addUserToRoleBatchCommon(idList, roleId!!)
    }

    fun addUserToRoleBatchCommon(userIdList: List<String>, roleId: String): Boolean {
        logger.info("add user to role batch userId : [$userIdList], roleId : [$roleId]")
        checkUserOrCreateUser(userIdList)
        checkRoleExist(roleId)
        val query = UserQueryHelper.getUserByIdList(userIdList)
        val update = UserUpdateHelper.buildAddRole(roleId)
        mongoTemplate.updateMulti(query, update, TUser::class.java)
        return true
    }

    fun removeUserFromRoleBatchCommon(userIdList: List<String>, roleId: String): Boolean {
        logger.info("remove user from role  batch userId : [$userIdList], roleId : [$roleId]")
        checkUserExistBatch(userIdList)
        checkRoleExist(roleId)
        val query = UserQueryHelper.getUserByIdListAndRoleId(userIdList, roleId)
        val update = UserUpdateHelper.buildUnsetRoles()
        mongoTemplate.updateMulti(query, update, TUser::class.java)
        return true
    }

    fun removeUserFromRole(userIdList: List<String>, roleId: String): Boolean {
        logger.info("remove user from role  batch userId : [$userIdList], roleId : [$roleId]")
        checkUserOrCreateUser(userIdList)
        checkRoleExist(roleId)
        val query = UserQueryHelper.getUserByIdListAndRoleId(userIdList, roleId)
        val update = UserUpdateHelper.buildUnsetRoles()
        mongoTemplate.updateMulti(query, update, TUser::class.java)
        return true
    }


    private fun findUsableProjectTypeRoleId(roleId: String?, projectId: String): String {
        var tempRoleId = roleId ?: "${projectId}_role_${IDUtil.shortUUID()}"
        while (true) {
            val role = roleRepository.findFirstByRoleIdAndProjectId(tempRoleId, projectId)
            if (role == null) return tempRoleId else tempRoleId = "${projectId}_role_${IDUtil.shortUUID()}"
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractServiceImpl::class.java)
    }
}
