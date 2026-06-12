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

    /**
     * 分批为用户添加角色，避免单次 updateMulti 操作文档数过多导致慢查询
     */
    fun addRoleToUsers(userIdList: List<String>, roleId: String) {
        val update = UserUpdateHelper.buildAddRole(roleId)
        userIdList.chunked(BATCH_SIZE).forEach { batch ->
            val query = UserQueryHelper.getUserByIdList(batch)
            this.updateMulti(query, update)
        }
    }

    /**
     * 分批从用户中移除角色，避免单次 updateMulti 操作文档数过多导致慢查询。
     *
     * 由于底层使用 \$pull 实现真删除（而非位置 \$unset），无需额外限定
     * `roles == roleId`，按 userId 范围批量执行即可；
     * 命令同时会把数组中残留的 null 一并清掉，对历史脏数据具备自愈能力。
     */
    fun removeRoleFromUsers(userIdList: List<String>, roleId: String) {
        val update = UserUpdateHelper.buildUnsetRoles(roleId)
        userIdList.chunked(BATCH_SIZE).forEach { batch ->
            val query = UserQueryHelper.getUserByIdList(batch)
            this.updateMulti(query, update)
        }
    }

    fun addUserToRole(userId: String, roleId: String) {
        val query = UserQueryHelper.getUserById(userId)
        val update = Update()
        update.push(TUser::roles.name, roleId)
        this.upsert(query, update)
    }

    fun removeUserFromRole(userId: String, roleId: String) {
        val query = UserQueryHelper.getUserByIdAndRoleId(userId, roleId)
        val update = UserUpdateHelper.buildUnsetRoles(roleId)
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

    /**
     * 清空指定用户的所有角色（覆盖式绑定全局预览角色前置步骤）
     */
    fun removeAllRolesFromUser(userId: String) {
        val query = Query(Criteria.where(TUser::userId.name).`is`(userId))
        val update = Update().set(TUser::roles.name, emptyList<String>())
        this.updateFirst(query, update)
    }

    /**
     * 在给定用户ID列表中，找出当前持有指定角色的用户（用于全局预览角色冲突检测）
     */
    fun findByUserIdInAndRolesContains(userIdList: List<String>, roleId: String): List<TUser> {
        val query = Query(
            Criteria().andOperator(
                Criteria.where(TUser::userId.name).`in`(userIdList),
                Criteria.where(TUser::roles.name).`is`(roleId)
            )
        )
        return this.find(query)
    }

    companion object {
        /** 批量更新时每批处理的用户数量，避免单次操作持锁时间过长 */
        private const val BATCH_SIZE = 50
    }

}
