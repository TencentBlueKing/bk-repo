-- KEYS[1]: sorted set key
-- ARGV[1]: max concurrent (limit)
-- ARGV[2]: safety TTL in milliseconds (per-connection expiry window, guards against crash leaks)
-- ARGV[3]: current time in milliseconds
-- ARGV[4]: connection UUID
-- Returns 1 if acquired, 0 if limit exceeded

-- clean up expired connection slots (crashed/timed-out connections)
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3])
-- count live slots
local current = redis.call('ZCARD', KEYS[1])
if current >= tonumber(ARGV[1]) then
    return 0
end
-- register this connection with its expiry timestamp as score
local expiryMs = tonumber(ARGV[3]) + tonumber(ARGV[2])
redis.call('ZADD', KEYS[1], expiryMs, ARGV[4])
return 1
