package com.tencent.bkrepo.auth.dao

import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.token.Token
import com.tencent.bkrepo.auth.pojo.user.UpdateUserRequest
import com.tencent.bkrepo.auth.util.DataDigestUtils
import com.tencent.bkrepo.auth.util.query.UserQueryHelper
import com.tencent.bkrepo.auth.util.query.UserUpdateHelper
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository


@Repository
class UserDao : SimpleMongoDao<TUser>() {

    fun addRoleToUsers(userIdList: List<String>, roleId: String) {
        val query = UserQueryHelper.getUserByIdList(userIdList)
        val update = UserUpdateHelper.buildAddRole(roleId)
        this.updateMulti(query, update)
    }

    fun removeRoleFromUsers(userIdList: List<String>, roleId: String) {
        val query = UserQueryHelper.getUserByIdListAndRoleId(userIdList, roleId)
        val update = UserUpdateHelper.buildUnsetRoles()
        this.updateMulti(query, update)
    }

    fun addUserToRole(userId: String, roleId: String) {
        val query = UserQueryHelper.getUserById(userId)
        val update = Update()
        update.push(TUser::roles.name, roleId)
        this.upsert(query, update)
    }

    fun removeUserFromRole(userId: String, roleId: String) {
        val query = UserQueryHelper.getUserByIdAndRoleId(userId, roleId)
        val update = UserUpdateHelper.buildUnsetRoles()
        this.upsert(query, update)
    }

    fun updateUserById(userId: String, request: UpdateUserRequest): Boolean {
        val query = UserQueryHelper.getUserById(userId)
        val update = UserUpdateHelper.buildUpdateUser(request)
        val result = this.updateFirst(query, update)
        if (result.modifiedCount == 1L) return true
        return false
    }

    fun removeTokenFromUser(userId: String, name: String) {
        val query = UserQueryHelper.getUserById(userId)
        val update = UserUpdateHelper.buildUnsetTokenName(name)
        this.updateFirst(query, update)
    }

    fun getUserByPassWordAndHash(userId: String, pwd: String, hashPwd: String, sm3HashPwd: String): TUser? {
        val query = UserQueryHelper.buildUserPasswordCheck(userId, pwd, hashPwd, sm3HashPwd)
        return this.findOne(query)
    }

    fun addUserToken(userId: String, userToken: Token) {
        val query = UserQueryHelper.getUserById(userId)
        val update = Update()
        update.addToSet(TUser::tokens.name, userToken)
        this.upsert(query, update)
    }

    fun getUserNotLocked(tenantId: String?): List<TUser> {
        val query = UserQueryHelper.filterNotLockedUser(tenantId)
        return this.find(query)
    }

    fun addUserAccount(userId: String, accountId: String): Boolean {
        val query = Query(Criteria(TUser::userId.name).isEqualTo(userId))
        val update = Update().addToSet(TUser::accounts.name, accountId)
        val record = this.updateFirst(query, update)
        if (record.modifiedCount == 1L || record.matchedCount == 1L) return true
        return false
    }

    fun removeUserAccount(userId: String, accountId: String): Boolean {
        val query = Query(Criteria(TUser::userId.name).isEqualTo(userId))
        val update = Update().pull(TUser::accounts.name, accountId)
        val record = this.updateFirst(query, update)
        if (record.modifiedCount == 1L || record.matchedCount == 1L) return true
        return false
    }

    fun countByName(userName: String?, admin: Boolean?, locked: Boolean?): Long {
        val query = UserQueryHelper.getUserByName(userName, admin, locked)
        return this.count(query)
    }

    fun getByPage(userName: String?, admin: Boolean?, locked: Boolean?, pageNumber: Int, pageSize: Int): List<TUser> {
        val query = UserQueryHelper.getUserByName(userName, admin, locked)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        return this.find(query.with(pageRequest), TUser::class.java)
    }

    fun getByUserIdAndPassword(userId: String, hashPassword: String): TUser? {
        val query = UserQueryHelper.getUserByIdAndPwd(userId, hashPassword)
        return this.findOne(query)
    }

    fun updatePasswordByUserId(userId: String, newPassword: String): Boolean {
        val updateQuery = UserQueryHelper.getUserById(userId)
        val update = UserUpdateHelper.buildPwdUpdate(newPassword)
        val record = this.updateFirst(updateQuery, update)
        if (record.modifiedCount == 1L || record.matchedCount == 1L) return true
        return false
    }

    fun getUserByAsstUser(userId: String): List<TUser> {
        val query = UserQueryHelper.getUserByAsstUsers(userId)
        return this.find(query)
    }

    fun removeByUserId(userId: String): Boolean {
        val query = Query(Criteria(TUser::userId.name).`is`(userId))
        val result = this.remove(query)
        if (result.deletedCount == 1L) return true
        return false
    }

    fun findFirstByUserId(userId: String): TUser? {
        val query = Query(Criteria(TUser::userId.name).`is`(userId))
        return this.findOne(query)
    }

    fun findAllByRolesIn(rids: List<String>): List<TUser> {
        val query = Query(Criteria(TUser::roles.name).`in`(rids))
        return this.find(query)
    }

    fun findFirstByUserIdAndRoles(userId: String, roleId: String): TUser? {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TUser::userId.name).`is`(userId),
                Criteria.where(TUser::roles.name).`is`(roleId)
            )
        )
        return this.findOne(query)
    }

    fun findFirstByUserIdAndRolesIn(userId: String, roleIdArray: List<String>): TUser? {
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where(TUser::userId.name).`is`(userId),
                Criteria.where(TUser::roles.name).`in`(roleIdArray)
            )
        )
        return this.findOne(query)
    }

    fun findFirstByToken(token: String): TUser? {
        val md5Id = DataDigestUtils.md5FromStr(token)
        val sm3Id = DataDigestUtils.sm3FromStr(token)
        val query = Query.query(
            Criteria().orOperator(
                Criteria.where("tokens._id").`is`(md5Id),
                Criteria.where("tokens._id").`is`(sm3Id)
            )
        )
        return this.findOne(query)
    }

    fun findAllAdminUsers(): List<TUser> {
        val query = Query(Criteria.where(TUser::admin.name).`is`(true))
        return this.find(query)
    }

}
