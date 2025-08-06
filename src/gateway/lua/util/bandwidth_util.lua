--[[
Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.

Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.

BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.

A copy of the MIT License is included in this file.
]]

local _M = {}

--[[
根据带宽数据选择最优实例
@param service_name 服务名称
@param ips 查询得到的IP列表
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
            if ts and tonumber(ts) and (current_time - tonumber(ts)) < 300 then
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

return _M