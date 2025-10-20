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

-- 敏感参数列表（不区分大小写）
local sensitive_params = {
    "token", "secretkey", "access_token", "accesstoken", 
    "authorization", "password", "passwd", "pwd", "secret"
}

-- 创建敏感参数查找表，提高性能
local sensitive_param_set = {}
for _, param in ipairs(sensitive_params) do
    sensitive_param_set[param] = true
end

-- 判断参数名是否为敏感参数
local function is_sensitive_param(param_name)
    local lower_name = string.lower(param_name)
    for sensitive, _ in pairs(sensitive_param_set) do
        if string.find(lower_name, sensitive, 1, true) then
            return true
        end
    end
    return false
end

-- 对参数值进行脱敏处理
local function mask_value(value)
    local SHOW_LENGTH = 3
    if #value < SHOW_LENGTH * 4 then
        return "******"
    else
        local prefix = string.sub(value, 1, SHOW_LENGTH)
        local suffix = string.sub(value, -SHOW_LENGTH)
        return prefix .. "***" .. suffix
    end
end

-- 对单个参数进行脱敏处理
local function mask_parameter(param)
    local equal_pos = string.find(param, "=", 1, true)
    if not equal_pos then
        return param
    end
    
    local key = string.sub(param, 1, equal_pos - 1)
    local value = string.sub(param, equal_pos + 1)
    
    if is_sensitive_param(key) then
        return key .. "=" .. mask_value(value)
    else
        return param
    end
end

-- 对查询字符串进行脱敏处理
local function mask_query_string(query_string)
    local params = {}
    for param in string.gmatch(query_string, "[^&]+") do
        table.insert(params, mask_parameter(param))
    end
    return table.concat(params, "&")
end

-- 对URL进行脱敏处理
local function mask_url(url)
    if not url or url == "" then
        return url
    end
    
    local question_pos = string.find(url, "?", 1, true)
    if not question_pos then
        return url
    end
    
    local base_url = string.sub(url, 1, question_pos - 1)
    local query_string = string.sub(url, question_pos + 1)
    
    if query_string == "" then
        return url
    end
    
    local masked_query = mask_query_string(query_string)
    return base_url .. "?" .. masked_query
end

-- 对请求行进行脱敏处理
local function mask_request_line(request_line)
    if not request_line or request_line == "" then
        return request_line
    end
    
    -- 解析请求行
    local parts = {}
    for part in string.gmatch(request_line, "%S+") do
        table.insert(parts, part)
    end
    
    if #parts < 2 then
        return request_line
    end
    
    local method = parts[1]
    local url = parts[2]
    local protocol = parts[3] or ""
    
    local masked_url = mask_url(url)
    
    if protocol ~= "" then
        return method .. " " .. masked_url .. " " .. protocol
    else
        return method .. " " .. masked_url
    end
end

-- 安全执行函数，捕获异常并返回默认值
local function safe_execute(func, default_value, ...)
    local success, result = pcall(func, ...)
    if success then
        return result
    else
        return default_value
    end
end

-- 设置脱敏后的请求行（带异常保护）
local request = ngx.var.request
local masked_request = safe_execute(mask_request_line, request, request)
ngx.var.masked_request = masked_request

ngx.var.request_time_ms = math.floor(tonumber(ngx.var.request_time) * 1000)