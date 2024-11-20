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
    return ips[math.random(table.getn(ips))] .. ":" .. port

end

return _M
