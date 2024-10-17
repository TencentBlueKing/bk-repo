redis.replicate_commands()
local tokens_key = KEYS[1]
local timestamp_key = KEYS[2]
-- 每秒填充速率
local rate = tonumber(ARGV[1])
-- 令牌桶最大数量
local capacity = tonumber(ARGV[2])
-- 消耗令牌数量
local requested = tonumber(ARGV[3])
-- now 当前时间秒
local currentTime = redis.call('TIME')
local now = tonumber(currentTime[1])
-- 计算令牌桶填充满需要多久
local fill_time = capacity/rate
-- *2保证时间充足
local ttl = math.floor(fill_time*2)

-- 防止小于0
if ttl < 1 then
    ttl = 10
end
-- 获取令牌桶内剩余数量
local last_tokens = tonumber(redis.call("get", tokens_key))
-- 第一次没有数值，设置桶为满的
if last_tokens == nil then
  last_tokens = capacity
end
-- 获取上次更新时间
local last_refreshed = tonumber(redis.call("get", timestamp_key))
if last_refreshed == nil then
  last_refreshed = 0
end
-- 本次校验和上次更新时间的间隔
local delta = math.max(0, now-last_refreshed)
-- 填充令牌，计算新的令牌桶剩余令牌数，填充不超过令牌桶上限
local filled_tokens = math.min(capacity, last_tokens+(delta*rate))

-- 判断令牌数量是否足够
local allowed = filled_tokens >= requested
local new_tokens = filled_tokens
local allowed_num = 0
if allowed then
  -- 如成功，令牌桶剩余令牌数减消耗令牌数
  new_tokens = filled_tokens - requested
  allowed_num = 1
end

-- 设置令牌桶剩余令牌数，令牌桶最后填充时间now, ttl超时时间
redis.call("setex", tokens_key, ttl, new_tokens)
redis.call("setex", timestamp_key, ttl, now)

return { allowed_num, new_tokens }
