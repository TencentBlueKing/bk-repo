--[[
Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.

Copyright (C) 2025 Tencent.  All rights reserved.

BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.

A copy of the MIT License is included in this file.


Terms of the MIT License:
---------------------------------------------------
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
]]

local _M = {}

-- 上报心跳信息到Redis
function _M:report_heartbeat()
    local red, err = redisUtil:new()
    if not red or err ~= nil then
        ngx.log(ngx.ERR, "failed to create redis connection for heartbeat")
        return
    end

    local timestamp = ngx.now()

    -- Redis key格式: bkrepo:gateway:heartbeat:{ip}
    local redis_key = "bkrepo:gateway:heartbeat:" .. ip

    -- 使用hash存储心跳信息
    local ok, err = red:hmset(redis_key,
            "ip", internal_ip,
            "tag", config.ns.tag,
            "timestamp", timestamp,
            "last_update", os.date("%Y-%m-%d %H:%M:%S", timestamp)
    )

    if not ok then
        ngx.log(ngx.ERR, "failed to report heartbeat to redis: ", err)
        red:close()
    end

    -- 设置过期时间为180秒（心跳间隔60秒，允许最多丢失2次心跳）
    red:expire(redis_key, 180)

    -- 同时维护一个gateway列表，用于快速查询所有在线的gateway
    local list_key = "bkrepo:gateway:list"
    red:sadd(list_key, ip)
    red:expire(list_key, 180)
    -- 将连接放回连接池
    local ok, err = red:set_keepalive(10000, 100)
    if not ok then
        ngx.log(ngx.ERR, "failed to set keepalive: ", err)
        red:close()
    end
end

return _M
