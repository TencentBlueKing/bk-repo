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

local _M = {}

--[[获取微服务真实地址]]
function _M:get_addr(service_name)

    local service_prefix = config.service_prefix

    if service_prefix == nil then
        service_prefix = ""
    end

    -- return k8s service address
    if ngx.var.name_space ~= "" then
        return service_prefix .. service_name .. "." .. ngx.var.name_space .. ".svc.cluster.local"
    end

    -- boot assembly部署
    if config.service_name ~= nil and config.service_name ~= "" then
        local domains = stringUtil:split(config.bkrepo.domain, ";")
        return domains[math.random(1, #domains)]
    end

    local ns_config = config.ns
    local tag = ns_config.tag

    local query_subdomain = tag .. "." .. service_prefix .. service_name .. ".service." .. ns_config.domain

    local ips = {} -- address
    local port = nil -- port

    local router_srv_cache = ngx.shared.router_srv_store
    local router_srv_value = router_srv_cache:get(query_subdomain)
    local service_in_local = config.service_in_local

    if router_srv_value == nil then
        -- 是否取用本地配置, 取用本地配置时需要获取所有ip,使用tcp协议，并增加缓存时间
        local cache_time = 2
        local use_udp = true
        if service_in_local ~= nil and service_in_local ~= "" then
            cache_time = 3
            use_udp = false
        end

        if not ns_config.ip then
            ngx.log(ngx.ERR, "DNS ip not exist!")
            ngx.exit(503)
            return
        end

        local dnsIps = {}
        if type(ns_config.ip) == 'table' then
            for _, v in ipairs(ns_config.ip) do
                table.insert(dnsIps, { v, ns_config.port })
            end
        else
            table.insert(dnsIps, { ns_config.ip, ns_config.port })
        end

        local dns, err = resolver:new {
            nameservers = dnsIps,
            retrans = 5,
            timeout = 2000
        }

        if not dns then
            ngx.log(ngx.ERR, "failed to instantiate the resolver: ", err)
            ngx.exit(503)
            return
        end
        if use_udp then
            records, err = dns:query(query_subdomain, { qtype = dns.TYPE_SRV, additional_section = true })
        else
            records, err = dns:tcp_query(query_subdomain, { qtype = dns.TYPE_SRV, additional_section = true })
        end
        if not records then
            ngx.log(ngx.ERR, "failed to query the DNS server: ", err)
            ngx.exit(503)
            return
        end

        if records.errcode then
            if records.errcode == 3 then
                ngx.log(ngx.ERR, "DNS error code #" .. records.errcode .. ": ", records.errstr)
                ngx.exit(503)
                return
            else
                ngx.log(ngx.ERR, "DNS error #" .. records.errcode .. ": ", err)
                ngx.exit(503)
                return
            end
        end

        for _, v in pairs(records) do
            if v.section == dns.SECTION_AN then
                port = v.port
            end

            if v.section == dns.SECTION_AR then
                table.insert(ips, v.address)
            end
        end

        local ip_len = table.getn(ips)
        if ip_len == 0 or port == nil then
            ngx.log(ngx.ERR, "DNS answer didn't include ip or a port , ip len" .. ip_len .. " port " .. port)
            ngx.exit(503)
            return
        end
        router_srv_cache:set(query_subdomain, table.concat(ips, ",") .. ":" .. port, cache_time)
    else
        local func_itor = string.gmatch(router_srv_value, "([^:]+)")
        local ips_str = func_itor()
        port = func_itor()

        for ip in string.gmatch(ips_str, "([^,]+)") do
            table.insert(ips, ip)
        end
    end
    -- return with local service
    if internal_ip ~= nil and service_in_local ~= nil and string.find(service_in_local, service_name) ~= nil then
        local service_ip = string.gsub(internal_ip, "\n", "")
        if arrayUtil:isInArray(service_ip, ips) then
            return "127.0.0.1:" .. port
        end
    end

    -- return ip,port address
    -- 检查当前服务是否在需要带宽判断的服务列表中
    -- ip带宽检查逻辑
    local service_with_bandwidth_check = config.service_with_bandwidth_check
    if string.find(service_with_bandwidth_check or "", service_name) then
        -- 使用共享内存缓存带宽检查结果
        local bw_cache = ngx.shared.bw_cache_store
        local cache_key = "bw:" .. service_name
        local cached_result = bw_cache:get(cache_key)

        -- 如果缓存存在且未过期
        if cached_result then
            -- 获取缓存IP列表
            local cached_ip_list = {}
            for ip in string.gmatch(cached_result, "([^,]+)") do
                table.insert(cached_ip_list, ip)
            end

            -- 与当前可用IP列表取交集,避免ip已经失效
            local valid_ips = {}
            local ip_set = {}
            for _, ip in ipairs(ips) do
                ip_set[ip] = true
            end

            for _, cached_ip in ipairs(cached_ip_list) do
                if ip_set[cached_ip] then
                    table.insert(valid_ips, cached_ip)
                end
            end

            -- 如果交集不为空，随机返回一个
            if #valid_ips > 0 then
                return valid_ips[math.random(#valid_ips)] .. ":" .. port
            end
            ngx.log(ngx.WARN, "all cached IPs are invalid")
        end

        -- 执行带宽检查获取符合条件的IP列表
        local service_key = service_prefix .. service_name
        local min_bw_instances, err = self:get_min_bandwidth_instances(service_key, ips)
        -- 如果获取到符合条件的IP列表
        if min_bw_instances and #min_bw_instances > 0 then
            -- 将整个IP列表存入缓存，用逗号分隔
            bw_cache:set(cache_key, table.concat(min_bw_instances, ","), 10)
            -- 随机返回列表中的一个IP
            return min_bw_instances[math.random(#min_bw_instances)] .. ":" .. port
        end
        ngx.log(ngx.WARN, "bandwidth check failed，err : ", err or "unknown error")
    end
    -- 2. Redis获取失败时回退到随机选择
    return ips[math.random(table.getn(ips))] .. ":" .. port

end

--[[
根据带宽数据选择最优实例
@param service_name 服务名称
@param ips 查询得到的IP列表
@param port 服务端口
@return 最优实例列表(ips)或nil, 错误信息
]]
function _M:get_min_bandwidth_instances(service_name, ips)
    -- Redis键定义
    local active_threshold = 300 -- 5分钟活跃阈值
    -- 从redis获取
    local red, err = redisUtil:new()
    if not red then
        return nil, "host_util failed to new redis: " .. (err or "unknown error")
    end

    -- 1. 获取服务实例列表（单次Redis调用）
    local service_key = "bw:service:" .. service_name
    local service_instances, err = red:smembers(service_key)
    if not service_instances then
        return nil, "failed to get service instances: " .. (err or "unknown error")
    end

    -- 2. 获取实例带宽数据（批量获取）
    local instance_total_key = "bw:instance:total_bandwidth"
    local bandwidths = {}
    for _, ip in ipairs(service_instances) do
        local bw = red:zscore(instance_total_key, ip)
        if bw then
            bandwidths[ip] = tonumber(bw)
        end
    end

    -- 3. 过滤活跃实例（5分钟阈值）
    local active_instances = {}
    local current_time = os.time()
    for _, ip in ipairs(service_instances) do
        if bandwidths[ip] then
            local ts_key = service_key .. ":ts"
            local ts = red:hget("bw:instance:" .. ip .. ":services", ts_key)
            if ts and (current_time - tonumber(ts)) < 300 then
                table.insert(active_instances, {
                    ip = ip,
                    bandwidth = bandwidths[ip]
                })
            end
        end
    end

    -- 4. 排序并选择最优实例
    table.sort(active_instances, function(a, b)
        return a.bandwidth < b.bandwidth
    end)

    -- 5. 返回与输入IP列表的交集
    local result = {}
    local ip_set = {}
    for _, ip in ipairs(ips) do
        ip_set[ip] = true
    end

    for _, instance in ipairs(active_instances) do
        if ip_set[instance.ip] then
            table.insert(result, instance.ip)
            if #result >= math.max(2, #active_instances / 4) then
                break
            end
        end
    end

    --- 将redis连接放回pool中
    red:set_keepalive(config.redis.max_idle_time, config.redis.pool_size)

    return #result > 0 and result or nil
end

--[[根据路由表获取转发域名]]
function _M:get_target_by_project()
    local headers = ngx.req.get_headers()
    local bkrepo_project_id = stringUtil:getValue(headers["x-bkrepo-project-id"])
    local devops_project_id = stringUtil:getValue(headers["x-devops-project-id"])
    local devops_project_id_uri = urlUtil:parseUrl(ngx.var.request_uri)["x-devops-project-id"]

    -- 优先判断 X-BKREPO-PROJECT-ID 的值
    local projectId
    if bkrepo_project_id and bkrepo_project_id ~= "" then
        projectId = bkrepo_project_id
    else
        if devops_project_id and devops_project_id ~= "" then
            projectId = devops_project_id
        end
        if devops_project_id_uri and devops_project_id_uri ~= "" then
            projectId = devops_project_id_uri
        end
    end

    if projectId and config.project_router and config.router_domain then
        local env = config.project_router[projectId]
        if env and config.router_domain[env] then
            return env, config.router_domain[env]
        end
    end
    return nil, nil
end

--[[随机获取路由]]
function _M:get_target_by_random(env)
    if config.router_domain == nil or next(config.router_domain) then
        return nil
    end
    for k, v in pairs(config.router_domain) do
        if k ~= env and v ~= nil then
            return v
        end
    end
    return nil
end

return _M