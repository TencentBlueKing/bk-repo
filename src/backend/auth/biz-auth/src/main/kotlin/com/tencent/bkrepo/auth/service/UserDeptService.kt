package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.pojo.user.UserOrgMembership

/**
 * 用户组织归属查询 SPI。
 *
 * 临时 token / 作品分享等按组织授权时，通过本接口解析用户所属组织范围（自由 type+value）。
 * 业务方可自行实现本接口并注册为 Spring Bean；未提供实现时使用空操作占位，返回空组织范围。
 */
interface UserDeptService {
    /**
     * 查询用户所属组织。
     *
     * @throws IllegalArgumentException userId 为空（由具体实现决定是否校验）
     * @throws Exception 上游查询失败时抛出，调用方应按拒绝访问处理
     */
    fun getUserOrgMembership(userId: String): UserOrgMembership
}
