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

_M = {}

function _M:verify_ticket(bk_ticket, input_type)
    local user_cache = ngx.shared.user_info_store
    local user_cache_value = user_cache:get(bk_ticket)
    if user_cache_value == nil then
        --- 初始化HTTP连接
        local httpc = http.new()
        --- 开始连接
        httpc:set_timeout(3000)
        httpc:connect(config.oauth.ip, config.oauth.port)

        --- 组装请求body
        if input_type == "ticket" then
            requestBody = {
                env_name = config.oauth.env,
                app_code = config.oauth.app_code,
                app_secret = config.oauth.app_secret,
                grant_type = "authorization_code",
                id_provider = "bk_login_ied",
                bk_ticket = bk_ticket
            }
        else
            requestBody = {
                grant_type = "authorization_code",
                id_provider = "bk_login",
                bk_token = bk_ticket
            }
        end

        --- 转换请求内容
        local requestBodyJson = json.encode(requestBody)
        if requestBodyJson == nil then
            ngx.log(ngx.ERR, "failed to encode auth/token request body: ", logUtil:dump(requestBody))
            ngx.exit(401)
            return
        end

        --- 发送请求
        -- local url = config.oauth.scheme .. config.oauth.ip  .. config.oauth.loginUrl .. bk_token
        local url = config.oauth.url
        local httpHeaders
        if input_type == "ticket" then
            httpHeaders = {
                ["Host"] = config.oauth.host,
                ["Accept"] = "application/json",
                ["Content-Type"] = "application/json"
            }
        else
            httpHeaders = {
                ["Host"] = config.oauth.host,
                ["Accept"] = "application/json",
                ["Content-Type"] = "application/json",
                ["X-BK-APP-CODE"] = config.oauth.app_code,
                ["X-BK-APP-SECRET"] = config.oauth.app_secret
            }
        end
        local res, err = httpc:request({
            path = url,
            method = "POST",
            headers = httpHeaders,
            body = requestBodyJson
        })
        --- 判断是否出错了
        if not res then
            ngx.log(ngx.ERR, "failed to request get_ticket: ", err)
            ngx.exit(401)
            return
        end
        --- 判断返回的状态码是否是200
        if res.status ~= 200 then
            ngx.log(ngx.STDERR, "failed to request get_ticket, status: ", res.status)
            ngx.exit(401)
            return
        end
        --- 获取所有回复
        local responseBody = res:read_body()
        --- 设置HTTP保持连接
        httpc:set_keepalive(60000, 5)
        --- 转换JSON的返回数据为TABLE
        local result = json.decode(responseBody)
        --- 判断JSON转换是否成功
        if result == nil then
            ngx.log(ngx.ERR, "failed to parse get_ticket response：", responseBody)
            ngx.exit(401)
            return
        end

        --- 判断返回码:Q!
        if result.code ~= 0 then
            ngx.log(ngx.INFO, "invalid get_ticket: ", result.message)
            ngx.exit(401)
            return
        end
        if input_type == "ticket" then
            user_cache_value = result.data.user_id
        else
            user_cache_value = result.data.identity.username
        end
        user_cache:set(bk_ticket, user_cache_value, 180)
    end
    return user_cache_value
end

function _M:verify_bk_token(auth_url, token)
    local user_cache = ngx.shared.user_info_store
    local user_cache_value = user_cache:get(token)
    if user_cache_value == nil then
        local http_cli = http.new()
        local auth = config.oauth
        local query = "bk_app_code=" .. auth.app_code .. "&bk_app_secret=" .. auth.app_secret .. "&bk_token=" .. token
        local addr = "http://" .. auth_url .. "/api/c/compapi/v2/bk_login/get_user/?" .. query
        --- 开始连接
        http_cli:set_timeout(3000)
        http_cli:connect(addr)
        --- 发送请求
        local res, err = http_cli:request_uri(addr, {
            method = "GET",
        })
        --- 判断是否出错了
        if not res then
            ngx.log(ngx.ERR, "failed to request apigw: error", err)
            ngx.exit(401)
            return
        end
        --- 判断返回的状态码是否是200
        if res.status ~= 200 then
            ngx.log(ngx.STDERR, "failed to request apigw, status: ", res.status)
            ngx.exit(401)
            return
        end
        --- 转换JSON的返回数据为TABLE
        local result = json.decode(res.body)
        --- 判断JSON转换是否成功
        if result == nil then
            ngx.log(ngx.ERR, "failed to parse apigw  response：", res.body)
            ngx.exit(401)
            return
        end

        --- 判断返回码:Q!
        if result.code ~= 0 then
            if result.code == 1302403 then
                ngx.log(ngx.ERR, "is_login code is 1302403 , need Authentication")
                ngx.header["X-DEVOPS-ERROR-RETURN"] = '{"code": 440,"message": "' .. result.message .. '", "data": 1302403,"traceId":null }'
                ngx.header["X-DEVOPS-ERROR-STATUS"] = 440
                ngx.exit(401)
            end
            ngx.log(ngx.INFO, "invalid user token: ", result.message)
            ngx.exit(401)
            return
        end
        user_cache_value = result.data.bk_username
        user_cache:set(token, user_cache_value, 180)
    end
    return user_cache_value
end

function _M:verify_bkrepo_token(bkrepo_login_token)
    local user_cache = ngx.shared.user_info_store
    local user_cache_value = user_cache:get(bkrepo_login_token)
    if user_cache_value == nil then
        --- 初始化HTTP连接
        local httpc = http.new()
        local addr = "http://" .. hostUtil:get_addr("auth")
        local path = "/api/user/verify?bkrepo_ticket=" .. bkrepo_login_token
        if config.service_name ~= nil and config.service_name ~= "" then
            path = "/auth" .. path
        end

        --- 开始连接
        httpc:set_timeout(3000)
        httpc:connect(addr)
        --- 发送请求
        local res, err = httpc:request_uri(addr, {
            path = path,
            method = "GET",
            headers = {
                ["authorization"] = config.bkrepo.authorization,
                ["Accept"] = "application/json",
                ["Content-Type"] = "application/json",
            }
        })
        --- 判断是否出错了
        if not res then
            ngx.log(ngx.ERR, "failed to request verify_bkrepo_token: ", err)
            ngx.exit(401)
            return
        end
        --- 判断返回的状态码是否是200
        if res.status ~= 200 then
            ngx.log(ngx.STDERR, "failed to request verify_bkrepo_token, status: ", res.status)
            ngx.exit(401)
            return
        end
        --- 设置HTTP保持连接
        httpc:set_keepalive(60000, 5)
        --- 转换JSON的返回数据为TABLE
        local result = json.decode(res.body)
        --- 判断JSON转换是否成功
        if result == nil then
            ngx.log(ngx.ERR, "failed to parse verify_bkrepo_token response：", res.body)
            ngx.exit(401)
            return
        end

        --- 判断返回码:Q!
        if result.code ~= 0 then
            ngx.log(ngx.INFO, "invalid verify_bkrepo_token: ", result.message)
            ngx.exit(401)
            return
        end
        user_cache_value = result.data.user_id
        user_cache:set(bkrepo_login_token, user_cache_value, 180)
    end
    return user_cache_value
end

function _M:verify_ci_token(ci_login_token)
    local user_cache = ngx.shared.user_info_store
    local user_cache_value = user_cache:get(ci_login_token)
    if user_cache_value == nil then
        --- 初始化HTTP连接
        local httpc = http.new()
        --- 开始连接
        httpc:set_timeout(3000)
        httpc:connect(config.bkci.host, config.bkci.port)
        local res, err = httpc:request({
            path = '/auth/api/external/third/login/verifyToken',
            method = "GET",
            headers = {
                ["Host"] = config.bkci.host,
                ["Accept"] = "application/json",
                ["Content-Type"] = "application/json",
                ["X-DEVOPS-CI-LOGIN-TOKEN"] = ci_login_token
            }
        })
        --- 判断是否出错了
        if not res then
            ngx.log(ngx.ERR, "failed to request get_ticket: ", err)
            ngx.exit(401)
            return
        end
        --- 判断返回的状态码是否是200
        if res.status ~= 200 then
            ngx.log(ngx.STDERR, "failed to request get_ticket, status: ", res.status)
            ngx.exit(401)
            return
        end
        --- 转换请求内容
        local responseBody = res:read_body()
        --- 设置HTTP保持连接
        httpc:set_keepalive(60000, 5)
        --- 转换JSON的返回数据为TABLE
        local result = json.decode(responseBody)
        --- 判断JSON转换是否成功
        if result == nil then
            ngx.log(ngx.ERR, "failed to parse get_ticket response：", responseBody)
            ngx.exit(401)
            return
        end
        user_cache_value = result.data
        user_cache:set(ci_login_token, user_cache_value, 60)
    end
    return user_cache_value
end

function _M:verify_tai_token(tai_token)
    local user_cache = ngx.shared.user_info_store
    local user_cache_value = user_cache:get(tai_token)
    if user_cache_value == nil then
        local http_cli = http.new()
        oauth = config.oauth
        local addr = "https://" .. oauth.url .. "/prod/bk_token_check/?bk_token=" .. tai_token
        local auth_content = '{"bk_app_code":"' .. oauth.app_code .. '","bk_app_secret":"' .. oauth.app_secret .. '"}'
        --- 开始连接
        http_cli:set_timeout(3000)
        http_cli:connect(addr)
        --- 发送请求
        local res, err = http_cli:request_uri(addr, {
            method = "GET",
            ssl_verify = false,
            headers = {
                ["X-Bkapi-Authorization"] = auth_content
            }
        })
        --- 判断是否出错了
        if not res then
            ngx.log(ngx.ERR, "failed to request tai token: error", err)
            ngx.exit(401)
            return
        end
        --- 判断返回的状态码是否是200
        if res.status ~= 200 then
            ngx.log(ngx.STDERR, "failed to request tai token, status: ", res.status)
            ngx.exit(401)
            return
        end
        --- 转换JSON的返回数据为TABLE
        local result = json.decode(res.body)
        --- 判断JSON转换是否成功
        if result == nil then
            ngx.log(ngx.ERR, "failed to parse tai token response：", res.body)
            ngx.exit(401)
            return
        end

        --- 判断返回名字
        if result.result ~= true or result.code ~= "00" then
            ngx.log(ngx.INFO, "invalid user tai token: ", result.message)
            ngx.exit(401)
            return
        end
        user_cache_value = result.data.username
        user_cache:set(tai_token, user_cache_value, 180)
    end
    return user_cache_value
end

return _M
