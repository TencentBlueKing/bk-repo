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


local service_name = ngx.var.service
if config.service_name ~= nil and config.service_name ~= "" then
  service_name = config.service_name
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

<<<<<<< HEAD
local host, port = hostUtil:get_addr(service_name)
ngx.var.target = host .. ":" .. port
=======
-- 当服务器为job的时候指向job的域名
if service_name == "job" then
  ngx.var.target = config.job.domain
  return 
end

if service_name == "bkrepo" then
  ngx.var.target = config.bkrepo.domain
  return
end


-- 获取灰度设置
local devops_gray = grayUtil:get_gray()

-- ngx.log(ngx.ERR, "devops_gray:", devops_gray )
local ns_config = nil
if devops_gray ~= true then
  ns_config = config.ns
  -- ngx.log(ngx.ERR, "ns_config" )
else
  ns_config = config.ns_gray
  -- ngx.log(ngx.ERR, "ns_config_gray" )
end 

local query_subdomain = config.ns.tag .. "." .. service_name .. ".service." .. ns_config.domain


if not ns_config.ip then
  ngx.log(ngx.ERR, "DNS ip not exist!")
  ngx.exit(503)
  return
end 

local dnsIps = {}
if type(ns_config.ip) == 'table' then
  for i,v in ipairs(ns_config.ip) do
    table.insert(dnsIps,{v, ns_config.port})
  end
else
  table.insert(dnsIps,{ns_config.ip, ns_config.port})
end

local ips = {} -- address
local port = nil -- port

local router_srv_cache = ngx.shared.router_srv_store
local router_srv_value = router_srv_cache:get(query_subdomain)

if router_srv_value == nil then
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
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4

  local records, err = dns:query(query_subdomain, { qtype = dns.TYPE_SRV, additional_section = true })

<<<<<<< HEAD
=======
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

  for i, v in pairs(records) do
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
  router_srv_cache:set(query_subdomain, table.concat(ips, ",") .. ":" .. port, 2)

else
  local func_itor = string.gmatch(router_srv_value, "([^:]+)")
  local ips_str = func_itor()
  port = func_itor()

  for ip in string.gmatch(ips_str, "([^,]+)") do
    table.insert(ips, ip)
  end
end

ngx.var.target = ips[math.random(table.getn(ips))] .. ":" .. port

--local host_num = table.getn(records)
--local host_index = math.random(host_num)
--if records[host_index].port then
--  local target_ip = dns:query(records[host_index].target)[1].address
--  ngx.var.target = target_ip .. ":" .. records[host_index].port
--else
--  ngx.log(ngx.ERR, "DNS answer didn't include a port")
--  ngx.exit(503)
--end
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
