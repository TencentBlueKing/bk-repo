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

--- 蓝鲸API网关JWT解析工具 ---

local _M = {}

--- base64url解码（JWT使用的是URL安全的base64编码）
local function base64url_decode(input)
    -- 将base64url字符替换为标准base64字符
    local reminder = #input % 4
    if reminder > 0 then
        local padlen = 4 - reminder
        input = input .. string.rep("=", padlen)
    end
    input = input:gsub("-", "+"):gsub("_", "/")
    return ngx.decode_base64(input)
end

--- 解析JWT Token，分离header、payload、signature三个部分
--- @param token string JWT Token字符串
--- @return table|nil header, table|nil payload, string|nil signature, string|nil sign_input
local function split_jwt(token)
    local parts = {}
    for part in token:gmatch("[^%.]+") do
        table.insert(parts, part)
    end
    if #parts ~= 3 then
        return nil, nil, nil, nil
    end

    local header_json = base64url_decode(parts[1])
    local payload_json = base64url_decode(parts[2])
    local signature = base64url_decode(parts[3])

    if not header_json or not payload_json or not signature then
        return nil, nil, nil, nil
    end

    local cjson = require("cjson.safe")
    local header = cjson.decode(header_json)
    local payload = cjson.decode(payload_json)

    if not header or not payload then
        return nil, nil, nil, nil
    end

    -- sign_input 是用于验签的原始数据（header.payload）
    local sign_input = parts[1] .. "." .. parts[2]

    return header, payload, signature, sign_input
end

--- 使用RSA公钥验证JWT签名（RS512算法）
--- @param sign_input string 待验证的签名输入（header.payload的base64url编码）
--- @param signature string 解码后的签名数据
--- @param public_key_pem string RSA公钥（PEM格式）
--- @return boolean 验证是否通过
local function verify_rs512_signature(sign_input, signature, public_key_pem)
    local resty_rsa = require("resty.rsa")

    local pub, err = resty_rsa:new({
        public_key = public_key_pem,
        algorithm = "SHA512",
    })

    if not pub then
        ngx.log(ngx.ERR, "failed to create rsa public key: ", err)
        return false
    end

    local ok, err = pub:verify(sign_input, signature)
    if not ok then
        ngx.log(ngx.ERR, "failed to verify jwt signature: ", err)
        return false
    end

    return true
end

--- 解析并验证蓝鲸API网关的JWT Token
--- @param jwt_token string JWT Token字符串
--- @param public_key_pem string RSA公钥（PEM格式）
--- @return table|nil payload 验证通过时返回payload，否则返回nil
function _M:verify_gateway_jwt(jwt_token, public_key_pem)
    if not jwt_token or jwt_token == "" then
        ngx.log(ngx.ERR, "jwt token is empty")
        return nil
    end

    if not public_key_pem or public_key_pem == "" then
        ngx.log(ngx.ERR, "public key is empty")
        return nil
    end

    -- 解析JWT
    local header, payload, signature, sign_input = split_jwt(jwt_token)
    if not header or not payload then
        ngx.log(ngx.ERR, "failed to parse jwt token")
        return nil
    end

    -- 验证算法是否为RS512
    if header.alg ~= "RS512" then
        ngx.log(ngx.ERR, "unsupported jwt algorithm: ", header.alg)
        return nil
    end

    -- 验证签名
    local ok = verify_rs512_signature(sign_input, signature, public_key_pem)
    if not ok then
        ngx.log(ngx.ERR, "jwt signature verification failed")
        return nil
    end

    return payload
end

--- 从JWT payload中获取用户名
--- @param payload table JWT的payload数据
--- @return string|nil username
function _M:get_username(payload)
    if not payload or not payload.user then
        return nil
    end
    if payload.user.verified ~= true then
        ngx.log(ngx.WARN, "user not verified in jwt token")
        return nil
    end
    return payload.user.username
end

--- 从JWT payload中获取应用编码
--- @param payload table JWT的payload数据
--- @return string|nil app_code
function _M:get_app_code(payload)
    if not payload or not payload.app then
        return nil
    end
    if payload.app.verified ~= true then
        ngx.log(ngx.WARN, "app not verified in jwt token")
        return nil
    end
    return payload.app.app_code
end

return _M
