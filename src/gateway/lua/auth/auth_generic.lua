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


--- head 请求发送到generic服务，顺带鉴权
local httpc = http.new()
local addr = "http://" .. hostUtil:get_addr("generic",true)
local path = string.gsub(ngx.var.request_uri, "/generic", "")
local headers = ngx.req.get_headers()

--- 开始连接
httpc:set_timeout(1000)
httpc:connect(addr)
--- 发送请求
local res, err = httpc:request_uri(addr, {
    path = path,
    method = "HEAD",
    headers = headers
})
--- 判断是否出错了
if not res or res.status ~= 200 then
    ngx.log(ngx.ERR, "failed to head resource: ", err)
    ngx.header["x-bkrepo-generic-auth-status"] = 401
    ngx.exit(200)
    return
end
ngx.header["x-bkrepo-generic-auth-status"] = 200
ngx.exit(200)
