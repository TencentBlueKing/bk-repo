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

package com.tencent.bkrepo.auth.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class DevopsAuthConfig {

    /**
     * ci auth 服务器地址
     */
    @Value("\${auth.devops.ciAuthServer:}")
    private var ciAuthServer: String = ""

    /**
     * ci auth token
     */
    @Value("\${auth.devops.ciAuthToken:}")
    private var ciAuthToken: String = ""

    /**
     * 是否允许超级管理员账号
     */
    @Value("\${auth.devops.enableSuperAdmin: false}")
    var enableSuperAdmin: Boolean = false

    /**
     * 是否开启目录权限校验
     */
    @Value("\${auth.devops.enablePathCheck: false}")
    var enablePathCheck: Boolean = false

    /**
     * 蓝盾平台appId集合
     */
    @Value("\${auth.devops.appIdSet:}")
    var devopsAppIdSet: String = ""

    /**
     * 允许通过默认密码访问用户set
     */
    @Value("\${auth.devops.userIdSet:}")
    var userIdSet: String = ""

    /**
     * 允许默认密码校验
     */
    @Value("\${auth.allowDefaultPwd: true}")
    var allowDefaultPwd: Boolean = true



    fun getBkciAuthServer(): String {
        return if (ciAuthServer.startsWith("http://") || ciAuthServer.startsWith("https://")) {
            ciAuthServer.removeSuffix("/")
        } else {
            "http://$ciAuthServer"
        }
    }

    fun setBkciAuthServer(authServer: String) {
        ciAuthServer = authServer
    }

    fun setBkciAuthToken(authToken: String) {
        ciAuthToken = authToken
    }

    fun getBkciAuthToken(): String {
        return ciAuthToken
    }
}
