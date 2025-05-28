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
      if not redis_cache_status  then
        cluster_cache_status = "up"
      else
        cluster_cache_status = redis_cache_status
      end
    end
    cluster_status:set(key, cluster_cache_status, 60)
    red:set_keepalive(config.redis.max_idle_time, config.redis.pool_size)
  end
  return cluster_cache_status ~= "down"
end

return _M