-- key: 限流器的键名
-- limit: 限流器的容量
-- interval: 时间窗口的长度（单位为秒）
-- count: 一次获取的令牌数量
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local interval = tonumber(ARGV[2])
local count = tonumber(ARGV[3])
local now_mill = tonumber(ARGV[4])
local now_sec = tonumber(ARGV[5])
local random = tonumber(ARGV[6])

-- 删除时间窗口之外的令牌
redis.call('zremrangebyscore', key, 0, now_sec - interval)

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
-- 生成令牌，并返回生成的令牌数量
local tokens = {}
for i = 1, count do
    table.insert(tokens, now_sec)
    table.insert(tokens, random..i)
end
redis.call('zadd', key, unpack(tokens))
redis.call('expire', key, interval)
return { allowed_num, remaining }
