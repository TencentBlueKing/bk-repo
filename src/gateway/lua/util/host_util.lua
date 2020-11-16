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
function _M:getAddr(service_name)

    service_name = "auth"
    local service_prefix = config.service_prefix

    if service_prefix == nil or service_prefix == "" then
        service_prefix = "repo-"
    end

    local ns_config = config.ns
    local query_subdomain = config.ns.tag .. "." .. service_prefix .. service_name .. ".service." .. ns_config.domain

    if not ns_config.ip then
        ngx.log(ngx.ERR, "DNS ip not exist!")
        ngx.exit(503)
        return
    end

    local dnsIps = {}
    if type(ns_config.ip) == 'table' then
        for i, v in ipairs(ns_config.ip) do
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

    local records, err = dns:query(query_subdomain, { qtype = dns.TYPE_SRV })

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

    local host_num = table.getn(records)
    local host_index = math.random(host_num)
    if records[host_index].port then
        local target_ip = dns:query(records[host_index].target)[1].address
        return target_ip, records[host_index].port
    else
        ngx.log(ngx.ERR, "DNS answer didn't include a port")
        ngx.exit(503)
    end
end

return _M