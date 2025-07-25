--[[
Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.

Copyright (C) 2019 Tencent.  All rights reserved.

BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.

A copy of the MIT License is included in this file.


Terms of the MIT License:
---------------------------------------------------
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
]]

string = require("string")
math = require("math")
json = require("cjson.safe")
uuid = require("resty.jit-uuid")
resolver = require("resty.dns.resolver")
ck = require("resty.cookie")
http = require("resty.http")
stringUtil = require("util.string_util")
logUtil = require("util.log_util")
redisUtil = require("util.redis_util")
md5 = require("resty.md5")
arrayUtil = require("util.array_util")
cookieUtil = require("util.cookie_util")
urlUtil = require("util.url_util")
oauthUtil = require("util.oauth_util")
hostUtil = require("util.host_util")
healthUtil = require("util.health_util")

math.randomseed(os.time())
uuid.seed()

local handle = io.popen("/sbin/ifconfig eth1 | grep 'inet ' | awk '{print $2}'")
local ip = handle:read("*a")
handle:close()
internal_ip = ip

local ok_table = {
  status = 0,
  data = true
}

response_ok = json.encode(ok_table)

