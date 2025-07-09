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

local _M = {}

--[[判断字符串是否在数组中]]
function _M:check_path()
    local security_paths = config.security_paths
    if security_paths ~= nil and #security_paths ~= 0 then
        local method = ngx.req.get_method()
        local path = ngx.var.uri
        for _, item in ipairs(security_paths) do
            local pathPattern = "/web/" .. service_name .. item.path
            if service_name == "fs-server" then
                pathPattern = "/web/fs%-server" .. item.path
            end
            if string.find(path, "^" .. pathPattern) ~= nil and service_name == item.service and method == item.method then
                return true
            end
        end
        return false
    end
    return true
end

-- 获取集群健康状态
function _M:get_cluster_health_status(tag)
    -- 从缓存获取
    local key = "cluster:health:" .. tag
    local cluster_status = ngx.shared.cluster_status
    local cluster_cache_status = cluster_status:get(key)
    if cluster_cache_status == nil then
        -- 从redis获取
        local red, err = redisUtil:new()
        if not red or err ~= nil then
            cluster_cache_status = "up"
        else
            local redis_cache_status = red:get(key)
            if not redis_cache_status then
                cluster_cache_status = "up"
            else
                cluster_cache_status = redis_cache_status
            end
        end
        cluster_status:set(key, cluster_cache_status, 60)
        red:set_keepalive(config.redis.max_idle_time, config.redis.pool_size)
    end
    return cluster_cache_status == "up"
end

--[[随机获取路由]]
function _M:get_target_by_random(env)
    if config.router_domain == nil or next(config.router_domain) == nil then
        return nil
    end
    for k, v in pairs(config.router_domain) do
        if k ~= env and v ~= nil then
            return v
        end
    end
    return nil
end

--[[根据路由表获取转发域名]]
function _M:get_target_by_project()
    local headers = ngx.req.get_headers()
    local bkrepo_project_id = stringUtil:getValue(headers["x-bkrepo-project-id"])
    local devops_project_id = stringUtil:getValue(headers["x-devops-project-id"])
    local devops_project_id_uri = urlUtil:parseUrl(ngx.var.request_uri)["x-devops-project-id"]
    local bkrepo_project_id_uri = urlUtil:parseUrl(ngx.var.request_uri)["x-bkrepo-project-id"]
    -- 优先判断 X-BKREPO-PROJECT-ID 的值
    local projectId
    if bkrepo_project_id and bkrepo_project_id ~= "" then
        projectId = bkrepo_project_id
    else
        if devops_project_id and devops_project_id ~= "" and not stringUtil:startswith(ngx.var.request_uri, "/generic/ext/bkstore/atom") then
            projectId = devops_project_id
        end
        if devops_project_id_uri and devops_project_id_uri ~= "" then
            projectId = devops_project_id_uri
        end
        if bkrepo_project_id_uri and bkrepo_project_id_uri ~= "" then
            projectId = bkrepo_project_id_uri
        end
    end

    -- get router from config file
    if projectId and config.project_router and config.router_domain then
        local env = config.project_router[projectId]
        if env and config.router_domain[env] then
            return env, config.router_domain[env]
        end
    end
    -- get router from cache
    local router_cache = ngx.shared.router_srv_store
    local domain_key = "project:router_domain"
    local router_key = "project:project_router"
    local domain_content, err = router_cache:get(domain_key)
    if domain_content == nil or err ~= nil then
        return nil, nil
    end
    local domain_map = json.decode(domain_content)
    if not domain_map then
        return nil, nil
    end
    local router_content, err = router_cache:get(router_key)
    if router_content == nil or err ~= nil then
        return nil, nil
    end
    local router_map = json.decode(router_content)
    if not router_map then
        return nil, nil
    end
    if projectId and domain_map and router_map then
        local env = router_map[projectId]
        if env and domain_map[env] then
            return env, domain_map[env]
        end
    end
    return nil, nil
end

return _M