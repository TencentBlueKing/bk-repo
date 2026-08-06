-- key: 限流器的键名
-- limit: 限流器的容量
-- interval: 时间窗口的长度（单位为毫秒）
-- count: 一次获取的令牌数量
-- now_ms: 当前时间戳（毫秒）
-- unique_id: 调用方生成的唯一标识，用于保证并发时 zadd member 不碰撞
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local interval = tonumber(ARGV[2])
local count = tonumber(ARGV[3])
local now_ms = tonumber(ARGV[4])
local unique_id = ARGV[5]

-- 删除时间窗口之外的令牌
redis.call('zremrangebyscore', key, 0, now_ms - interval)

-- 获取当前时间窗口内的令牌数量
local current = tonumber(redis.call('zcard', key))

-- 如果当前令牌数量已经达到限流器的容量，则不再生成新的令牌
if current >= limit then
    return 0
end

-- 计算需要生成的令牌数量
local remaining = limit - current
local allowed_num = 0
if (remaining < count) then
    return { allowed_num, remaining }
end
allowed_num = 1
-- member 包含唯一标识，避免并发时 zadd 相同 member 导致计数失准
for i = 1, count do
    local member = unique_id .. ":" .. i
    redis.call('zadd', key, now_ms, member)
end
redis.call('pexpire', key, interval)
return { allowed_num, remaining }
