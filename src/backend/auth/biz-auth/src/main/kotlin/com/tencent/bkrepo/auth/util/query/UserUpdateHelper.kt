package com.tencent.bkrepo.auth.util.query

import com.mongodb.BasicDBObject
import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.user.UpdateUserRequest
import com.tencent.bkrepo.auth.util.DataDigestUtils
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime

object UserUpdateHelper {

    fun buildUnsetRoles(): Update {
        val update = Update()
        return update.unset("roles.$")
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
        return update.push(TUser::roles.name, roleId)
    }

    fun buildPwdUpdate(newHashPwd: String): Update {
        return Update().set(TUser::pwd.name, newHashPwd)
    }
}
