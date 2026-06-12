package com.tencent.bkrepo.auth.util.query

import com.mongodb.BasicDBObject
import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.user.UpdateUserRequest
import com.tencent.bkrepo.auth.util.DataDigestUtils
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime

object UserUpdateHelper {

    /**
     * 从 user.roles 中移除指定 roleId。
     *
     * 历史实现使用 `update.unset("roles.$")`，但 MongoDB 对数组元素执行 \$unset
     * 只会把目标位置置为 null，不会缩短数组，长期反复增删后会导致 roles 中堆积大量 null，
     * 造成 user.roles 字段异常膨胀。这里改为 \$pull 真正删除元素，
     * 并顺手把数组里残留的 null 也一并 pull 掉，让脏数据在写入路径上自愈。
     *
     * 等价 mongo 命令：
     *   { \$pull: { roles: { \$in: [roleId, null] } } }
     */
    fun buildUnsetRoles(roleId: String): Update {
        val cond = BasicDBObject()
        cond["\$in"] = listOf(roleId, null)
        return Update().pull(TUser::roles.name, cond)
    }

    fun buildUpdateUser(request: UpdateUserRequest): Update {
        val update = Update()
        request.pwd?.let {
            val pwd = DataDigestUtils.md5FromStr(request.pwd)
            update.set(TUser::pwd.name, pwd)
        }
        request.name?.let {
            update.set(TUser::name.name, request.name)
        }
        request.email?.let {
            update.set(TUser::email.name, request.email)
        }
        request.locked?.let {
            update.set(TUser::locked.name, request.locked)
        }
        request.phone?.let {
            update.set(TUser::phone.name, request.phone)
        }
        request.admin?.let {
            update.set(TUser::admin.name, request.admin)
        }
        request.tenantId?.let {
            update.set(TUser::tenantId.name, request.tenantId)
        }
        if (request.asstUsers.isNotEmpty()) {
            update.set(TUser::asstUsers.name, request.asstUsers)
        }
        return update.set(TUser::lastModifiedDate.name, LocalDateTime.now())
    }

    fun buildUnsetTokenName(name: String): Update {
        val s = BasicDBObject()
        s["name"] = name
        val update = Update()
        return update.pull(TUser::tokens.name, s)
    }

    fun buildAddRole(roleId: String): Update {
        val update = Update()
        return update.addToSet(TUser::roles.name, roleId)
    }

    fun buildPwdUpdate(newHashPwd: String): Update {
        return Update().set(TUser::pwd.name, newHashPwd)
    }
}
