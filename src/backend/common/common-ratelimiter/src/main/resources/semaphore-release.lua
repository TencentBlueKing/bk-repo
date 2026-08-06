-- KEYS[1]: sorted set key
-- ARGV[1]: connection UUID
-- precisely removes this connection's slot; no-op if already expired/cleared
return redis.call('ZREM', KEYS[1], ARGV[1])
