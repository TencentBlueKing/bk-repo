--[[
Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.

Copyright (C) 2019 Tencent.  All rights reserved.

BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.

A copy of the MIT License is included in this file.


Terms of the MIT License:
---------------------------------------------------
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
]]

--- 蓝鲸API网关JWT认证 ---
--- 从请求头 X-Bkapi-JWT 中获取JWT Token，使用RSA公钥验证签名，提取用户信息

local function do_auth()
    local jwtUtil = require("util.jwt_util")

    --- 获取JWT Token
    local jwt_token = ngx.var.http_x_bkapi_jwt
    if not jwt_token or jwt_token == "" then
        ngx.log(ngx.ERR, "missing X-Bkapi-JWT header")
        ngx.exit(401)
        return
    end

    --- 获取网关公钥（从配置中获取）
    if not config then
        ngx.log(ngx.ERR, "config is nil, init.lua may not be loaded correctly")
        ngx.exit(500)
        return
    end

    local public_key = config.bkapigw_public_key
    if not public_key or public_key == "" then
        ngx.log(ngx.ERR, "bkapigw_public_key is not configured, please check init.lua")
        ngx.exit(500)
        return
    end

    --- 解析并验证JWT
    local payload = jwtUtil:verify_gateway_jwt(jwt_token, public_key)
    if not payload then
        ngx.log(ngx.ERR, "failed to verify bkapigw jwt token")
        ngx.exit(401)
        return
    end

    --- 校验 app.verified 和 user.verified
    local user_verified = payload.user and payload.user.verified == true
    local app_verified = payload.app and payload.app.verified == true

    --- 获取应用编码
    local app_code = nil
    if app_verified then
        app_code = payload.app.app_code
    end

    --- 获取用户名（三步校验逻辑）
    local username = nil

    if user_verified then
        --- 1. user.verified 为 true，直接从 user.username 获取
        username = payload.user.username
        ngx.log(ngx.INFO, "get username from jwt user: ", username)
    elseif app_verified then
        --- 2. user.verified 为 false，app.verified 为 true，从请求头 X-Bkrepo-UID 获取用户名
        username = ngx.var.http_x_bkrepo_uid
        if not username or username == "" then
            ngx.log(ngx.ERR, "user not verified, app verified but X-Bkrepo-UID header is missing")
            ngx.exit(401)
            return
        end
        ngx.log(ngx.INFO, "get username from X-Bkrepo-UID header: ", username)
    else
        --- 3. 都为 false，直接返回 401
        ngx.log(ngx.ERR, "both user and app are not verified")
        ngx.exit(401)
        return
    end

    --- 设置用户信息到响应头，供 nginx auth_request 机制使用
    ngx.header["x-bkrepo-uid"] = username
    ngx.header["x-bkrepo-bk-token"] = ""
    ngx.header["x-bkrepo-access-token"] = ""
    if app_code then
        ngx.header["x-bkrepo-app-code"] = app_code
    end
    ngx.exit(200)
end

--- 使用 pcall 包裹，捕获所有运行时异常并记录详细错误信息
local ok, err = pcall(do_auth)
if not ok then
    ngx.log(ngx.ERR, "auth_bkapigw error: ", err)
    ngx.exit(500)
end
