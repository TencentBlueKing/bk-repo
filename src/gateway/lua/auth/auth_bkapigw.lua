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

local jwtUtil = require("util.jwt_util")

--- 获取JWT Token
local jwt_token = ngx.var.http_x_bkapi_jwt
if not jwt_token or jwt_token == "" then
    ngx.log(ngx.ERR, "missing X-Bkapi-JWT header")
    ngx.exit(401)
    return
end

--- 获取网关公钥（从配置中获取）
local public_key = config.bkapigw_public_key
if not public_key or public_key == "" then
    ngx.log(ngx.ERR, "bkapigw_public_key is not configured")
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

--- 获取用户名（user.verified 必须为 true）
local username = jwtUtil:get_username(payload)

--- 获取应用编码（app.verified 必须为 true）
local app_code = jwtUtil:get_app_code(payload)

--- 用户名和应用编码至少要有一个
if not username and not app_code then
    ngx.log(ngx.ERR, "no valid user or app in jwt token")
    ngx.exit(401)
    return
end

--- 如果用户名为空，使用应用编码作为用户名
if not username or username == "" then
    username = app_code
end

--- 设置用户信息到响应头，供 nginx auth_request 机制使用
ngx.header["x-bkrepo-uid"] = username
ngx.header["x-bkrepo-bk-token"] = ""
ngx.header["x-bkrepo-access-token"] = ""
if app_code then
    ngx.header["x-bkrepo-app-code"] = app_code
end
ngx.exit(200)
