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

package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.opdata.config.LocalUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.LocalDateTime
import java.util.Base64
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * opdata 本地用户信息查询接口。
 *
 * 接口路径与 auth 模块保持一致（`/api/user/info`、`/api/user/userinfo/{uid}`），
 * 前端只需将请求前缀从 `auth/api` 切换到 `opdata/api` 即可完成迁移。
 *
 * 本 Controller 直接通过 [LocalUserService] 读取 auth 共享的 `user` 集合，
 * 因此即便 auth 微服务不可用，前端仍能正常获取用户身份信息进入页面。
 *
 * 注意：这里仅做**只读**查询，不承担用户创建/更新等写操作（仍由 auth 负责）。
 */
@Tag(name = "opdata-user", description = "opdata 本地用户信息查询接口")
@RestController
@RequestMapping("/api/user")
class UserInfoController(
    private val localUserService: LocalUserService
) {

    /**
     * 获取当前登录用户的基础信息。
     *
     * 行为对齐 auth 的 `/user/info`：
     * - userId、displayName、tenantId 从网关注入的请求头中取得；
     * - 若本地 `user` 集合中已有该用户，则补充 `admin` 字段，避免前端二次请求。
     *
     * 与 auth 版本的差异：
     * - 不执行 `createOrUpdateUser` 写库逻辑，保持只读；用户入库由 auth 负责。
     */
    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    fun userInfo(
        @RequestHeader("x-bkrepo-uid") bkUserId: String?,
        @RequestHeader("x-bkrepo-display-name") displayName: String?,
        @RequestHeader("x-bk-tenant-id") tenantId: String?
    ): Response<Map<String, Any>> {
        val decodedName = if (displayName.isNullOrEmpty()) {
            ""
        } else {
            runCatching { String(Base64.getDecoder().decode(displayName)) }.getOrDefault("")
        }
        val userId = bkUserId.orEmpty()
        // 尝试补充 admin 字段，查询失败时降级为 false，不影响基础信息返回
        val admin = if (userId.isNotEmpty()) localUserService.isAdmin(userId) else false
        val result = mapOf(
            "userId" to userId,
            "displayName" to decodedName,
            "tenantId" to tenantId.orEmpty(),
            "admin" to admin
        )
        return ResponseBuilder.success(result)
    }

    /**
     * 按 userId 查询用户详情。
     *
     * 返回字段与 auth 模块的 `UserInfo` 对齐（admin、locked、group、email、phone、
     * asstUsers、tenantId、createdDate 等），前端无需改动字段引用。
     *
     * 权限：非管理员只能查询自己的信息，否则抛 [PermissionException]。
     */
    @Operation(summary = "按 userId 查询用户详情")
    @GetMapping("/userinfo/{uid}")
    fun userInfoById(@PathVariable uid: String): Response<Map<String, Any?>?> {
        preCheckContextUser(uid)
        val record = localUserService.findUser(uid) ?: return ResponseBuilder.success(null)
        val result = mapOf(
            "userId" to record.userId,
            "name" to record.name,
            "email" to record.email,
            "phone" to record.phone,
            "createdDate" to record.createdDate?.let(LocalDateTime::toString),
            "locked" to record.locked,
            "admin" to record.admin,
            "group" to record.group,
            "asstUsers" to record.asstUsers,
            "tenantId" to record.tenantId
        )
        return ResponseBuilder.success(result)
    }

    /**
     * 查询目标 uid 的权限检查：
     * - 全局管理员放行；
     * - 普通用户仅允许查询自己的信息，避免信息泄露。
     */
    private fun preCheckContextUser(uid: String) {
        val currentUserId = SecurityUtils.getUserId()
        if (currentUserId == uid) {
            return
        }
        if (localUserService.isAdmin(currentUserId)) {
            return
        }
        throw PermissionException()
    }
}
