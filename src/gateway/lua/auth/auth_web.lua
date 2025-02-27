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

--- 蓝鲸平台登录对接
--- 获取Cookie中bk_token 和 bk_ticket
local token, username, display_name, tenant_id

--- standalone模式下校验bkrepo_ticket
if config.mode == "standalone" or config.mode == "" or config.mode == nil then
    --- 跳过登录请求
    start_i = string.find(ngx.var.request_uri, "login")
    start_i2 = string.find(ngx.var.request_uri, "rsa")
    if start_i ~= nil or start_i2 ~= nil then
        return
    end
    local bkrepo_login_token = cookieUtil:get_cookie("bkrepo_ticket")
    if not bkrepo_login_token then
        ngx.exit(401)
        return
    end
    username = oauthUtil:verify_bkrepo_token(bkrepo_login_token)
    token = bkrepo_login_token
elseif config.auth_mode == "" or config.auth_mode == "token" then
    local bk_token = cookieUtil:get_cookie("bk_token")
    if not bk_token then
        ngx.exit(401)
        return
    end
    if config.enable_multi_tenant_mode ~= nil and config.enable_multi_tenant_mode == "true" then
        username, display_name, tenant_id = oauthUtil:verify_bk_token_muti_tenant(config.oauth.apigw_url, bk_token)
        -- 设置多租户相关信息 --
        ngx.header["x-bkrepo-display-name"] = ngx.encode_base64(display_name)
        ngx.header["x-bkrepo-tenant-id"] = tenant_id
    else
        username = oauthUtil:verify_bk_token(config.oauth.apigw_url, bk_token)
    end
    token = bk_token
elseif config.auth_mode == "ticket" then
    local bk_ticket = cookieUtil:get_cookie("bk_ticket")
    if bk_ticket == nil then
        bk_ticket = ngx.var.http_x_devops_bk_ticket
    end
    if bk_ticket == nil then
        bk_ticket = urlUtil:parseUrl(ngx.var.request_uri)["x-devops-bk-ticket"]
    end
    if bk_ticket ~= nil then
        username = oauthUtil:verify_ticket(bk_ticket, "ticket")
        token = bk_ticket
    else
        -- 校验移动网关登录态
        if config.mobileSiteToken ~= nil and config.mobileSiteToken ~= "" then
            local mobile_user = oauthUtil:verify_mobile_gateway()
            if mobile_user == nil then
                ngx.exit(401)
                return
            end
            username = mobile_user
        else
            ngx.exit(401)
            return
        end
    end
elseif config.auth_mode == "odc" then
    local bk_token = cookieUtil:get_cookie("bk_token")
    if bk_token == nil then
        bk_token = ngx.var.http_x_devops_bk_token
        if bk_token == nil then
            bk_token = urlUtil:parseUrl(ngx.var.request_uri)["x-devops-bk-token"]
        end
        if bk_token == nil then
            ngx.exit(401)
            return
        end
    end
    username = oauthUtil:verify_tai_token(bk_token)
    token = bk_token
elseif config.auth_mode == "ci" then
    local ci_login_token = cookieUtil:get_cookie("X-DEVOPS-CI-LOGIN-TOKEN")
    if not ci_login_token then
        ngx.exit(401)
        return
    end
    username = oauthUtil:verify_ci_token(ci_login_token)
    token = ci_login_token
end

--- 设置用户信息
ngx.header["x-bkrepo-uid"] = username
ngx.header["x-bkrepo-bk-token"] = token
ngx.header["x-bkrepo-access-token"] = token
ngx.exit(200)