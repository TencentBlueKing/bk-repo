local key = KEYS[1]
local limit = tonumber(ARGV[1])
local adder = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])
local current = tonumber(redis.call('get', key) or "0")
if current + adder > limit then
    return 0
else
    redis.call('incrby', key, adder)
    if (current == 0) then
          redis.call('EXPIRE', key, ttl)
    end
    return 1
end