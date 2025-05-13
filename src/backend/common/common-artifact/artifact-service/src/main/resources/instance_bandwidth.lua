 -- 参数定义
local serviceKey = KEYS[1]    -- 服务主键: bw:service:{serviceName}
local instanceServiceKey = KEYS[2] -- 主机服务键: bw:instance:{ip}:services
local instanceTotalKey = KEYS[3]   -- 主机总带宽键: bw:instance:total_bandwidth
local nodeIp = ARGV[1]         -- 实例IP
local upload = tonumber(ARGV[2])  -- 上传带宽
local download = tonumber(ARGV[3])  -- 下载带宽
local cosAsyncUpload = tonumber(ARGV[4])  -- 异步上传带宽
local timestamp = tonumber(ARGV[5]) -- 时间戳
local expireSeconds = tonumber(ARGV[6]) -- 过期时间


-- 1. 更新主机上该服务的带宽和时间戳
local setTs = redis.call('HSET', instanceServiceKey, serviceKey..":ts", timestamp)  -- 新增时间戳记录
local setupload = redis.call('HSET', instanceServiceKey, serviceKey..":upload", upload)
local setdownload = redis.call('HSET', instanceServiceKey, serviceKey..":download", download)
local setcos_async_upload = redis.call('HSET', instanceServiceKey, serviceKey..":cos_async_upload", cosAsyncUpload)

-- 2. 重新计算主机总带宽
local services = redis.call('HGETALL', instanceServiceKey)
local total = 0
for i = 1, #services, 2 do
    if not string.match(services[i], ":ts$") then  -- 跳过时间戳键
        total = total + tonumber(services[i+1])
    end
end

-- 3. 更新主机总带宽排序集合
local zaddtotal = redis.call('ZADD', instanceTotalKey, total, nodeIp)

-- 4. 将主机添加到服务的instance集合中
local saddip = redis.call('SADD', serviceKey, nodeIp)
local ipList = redis.call('SMEMBERS', serviceKey)
-- 5. 设置各键的过期时间
redis.call('EXPIRE', serviceKey, expireSeconds)
redis.call('EXPIRE', instanceServiceKey, expireSeconds)
redis.call('EXPIRE', instanceTotalKey, expireSeconds)

-- 6. 返回主机当前总带宽
return {total, setTs, setupload, setdownload, setcos_async_upload, services, zaddtotal, saddip, ipList, expireSeconds}
        