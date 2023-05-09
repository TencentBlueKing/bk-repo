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

package com.tencent.bkrepo.auth.message

import com.tencent.bkrepo.common.api.message.MessageCode

enum class AuthMessageCode(private val businessCode: Int, private val key: String) : MessageCode {

    AUTH_DUP_UID(1, "auth.dup.uid"),
    AUTH_USER_NOT_EXIST(2, "auth.user.not-exist"),
    AUTH_DELETE_USER_FAILED(3, "auth.delete.user.failed"),
    AUTH_USER_TOKEN_ERROR(4, "auth.user.token.error"),
    AUTH_ROLE_NOT_EXIST(5, "auth.role.not-exist"),
    AUTH_DUP_RID(6, "auth.dup.rid"),
    AUTH_DUP_PERMNAME(7, "auth.dup.perm-name"),
    AUTH_PERMISSION_NOT_EXIST(8, "auth.permission.not-exist"),
    AUTH_PERMISSION_FAILED(9, "auth.permission.failed"),
    AUTH_USER_PERMISSION_EXIST(10, "auth.user.permission-exist"),
    AUTH_ROLE_PERMISSION_EXIST(11, "auth.role.permission-exist"),
    AUTH_DUP_APPID(12, "auth.dup.appid"),
    AUTH_APPID_NOT_EXIST(13, "auth.appid.not-exist"),
    AUTH_AKSK_CHECK_FAILED(14, "auth.asksk.check-fail"),
    AUTH_DUP_CLUSTERID(15, "auth.dup.clusterid"),
    AUTH_CLUSTER_NOT_EXIST(16, "auth.cluster.not-exist"),
    AUTH_PROJECT_NOT_EXIST(17, "auth.project.not-exist"),
    AUTH_ASST_USER_EMPTY(18, "auth.group.asst.user.empty"),
    AUTH_USER_TOKEN_EXIST(19, "auth.user.token.exist"),
    AUTH_LOGIN_TOKEN_CHECK_FAILED(20, "auth.login.token.check-fail"),
    AUTH_REPO_NOT_EXIST(21, "auth.repo.not-exist"),
    AUTH_ROLE_USER_NOT_EMPTY(22, "auth.role.user.not-empty"),
    AUTH_USER_TOKEN_TIME_ERROR(23, "auth.user.token.time.error"),
    AUTH_DELETE_KEY_FAILED(24, "auth.delete.key.failed"),
    AUTH_DUP_KEY(25, "auth.dup.key"),
    AUTH_DUP_CLIENT_NAME(26, "auth.dup.client.name"),
    AUTH_CLIENT_NOT_EXIST(27, "auth.client.notexist"),
    AUTH_CODE_NOT_EXIST(28, "auth.code.notexist"),
    AUTH_CODE_CHECK_FAILED(29, "auth.code.checkfail"),
    AUTH_SECRET_CHECK_FAILED(30, "auth.secret.checkfail"),
    AUTH_OAUTH_TOKEN_CHECK_FAILED(31, "auth.oauth.token.checkfail"),
    AUTH_CREDENTIAL_AT_LEAST_ONE(32, "auth.credential.atleastone"),
    AUTH_OWNER_CHECK_FAILED(33, "auth.owner.checkfail"),
    AUTH_INVALID_TYPE(34, "auth.invalid.type"),
    AUTH_LOGIN_FAILED(35, "auth.login.failed"),
    AUTH_EXT_PERMISSION_NOT_EXIST(35, "auth.ext.permission.not-exist"),
    AUTH_USER_FORAUTH_NOT_PERM(36, "auth.user.forauth.not-perm"),
    AUTH_ACCOUT_FORAUTH_NOT_PERM(37, "auth.account.forauth.not-perm"),
    AUTH_ENTITY_USER_NOT_EXIST(38, "auth.entity.user.not-exist")
    ;

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 2
}
