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

-- 访问限制微服务 --
local allow_services = { "auth", "repository", "generic", "docker", "oci", "maven", "job",
                         "helm", "pypi", "opdata", "rpm", "s3", "git", "npm", "fs-server", "analyst",
                         "replication", "git", "nuget", "composer", "media", "ddc", "conan", "job-schedule", "websocket" }
local service_name = ngx.var.service

if not arrayUtil:isInArray(service_name, allow_services) then
    ngx.exit(404)
    return
end

if service_name == "docker" and ngx.var.assembly ~= nil and ngx.var.assembly ~= "" then
    service_name = "oci"
end

if not service_name then
    ngx.log(ngx.ERR, "failed with no service name")
    ngx.exit(503)
    return
end

if service_name == "" then
    ngx.log(ngx.ERR, "failed with empty service name")
    ngx.exit(503)
    return
end

-- 访问限制api --
local security_paths = config.security_paths
if security_paths ~= nil and #security_paths ~= 0 then
    local is_secure = false
    local method = ngx.req.get_method()
    local path = ngx.var.uri
    for _, item in ipairs(security_paths) do
        local pathPattern = "/web/" .. service_name .. item.path
        if string.find(path, "^" .. pathPattern) ~= nil and service_name == item.service and method == item.method then
            is_secure = true
        end
    end
    if is_secure == false then
        ngx.exit(422)
        return
    end
end

-- 访问限制的工具
local access_util = nil

-- 限制访问频率
if access_util then
    local access_result, err = access_util:isAccess()
    if not access_result then
        ngx.log(ngx.STDERR, "request excess!")
        ngx.exit(503)
        return
    end
end

-- 哪些服务需要转到容器中 --
local service_in_container = config.service_in_container
if service_in_container ~= nil and string.find(service_in_container, service_name) ~= nil then
    ngx.var.target = config.container_url .. "/" .. service_name
    return
end

ngx.var.target = hostUtil:get_addr(service_name)

if ngx.var.assembly ~= nil and ngx.var.assembly ~= "" then
    ngx.var.target = ngx.var.target .. "/" .. service_name
end


