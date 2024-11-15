--[[
Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.

Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.

BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.

A copy of the MIT License is included in this file.


Terms of the MIT License:
---------------------------------------------------
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
]]

--- 获取Url请求参数中bk_token 和 bk_ticket

local token, username

local bk_ticket = urlUtil:parseUrl(ngx.var.request_uri)["x-devops-bk-ticket"]
local bk_token = urlUtil:parseUrl(ngx.var.request_uri)["x-devops-bk-token"]
local platform_token = ngx.var.http_authorization

if platform_token ~= nil and string.find(string.lower(platform_token), "^platform") then
    ngx.header["x-bkrepo-authorization"] = platform_token
    ngx.header["x-bkrepo-uid"] = ngx.var.http_x_bkrepo_uid
    ngx.exit(200)
    return
end

if bk_ticket == nil and bk_token == nil then
    ngx.exit(401)
    return
end

if bk_ticket ~= nil then
    username = oauthUtil:verify_ticket(bk_ticket, "ticket")
    token = bk_ticket
end

if bk_token ~= nil then
    username = oauthUtil:verify_tai_token(bk_token)
    token = bk_token
end

--- 设置用户信息
ngx.header["authorization"] = config.bkrepo.authorization
ngx.header["x-bkrepo-uid"] = username
ngx.header["x-bkrepo-bk-token"] = token
ngx.header["x-bkrepo-access-token"] = token
ngx.exit(200)
