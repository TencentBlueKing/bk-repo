#!/bin/bash

project=$1
user=$2
authorization=$3

# 创建项目
curl -X POST http://dev1.bkrepo.oa.com/api/repository/api/project -H "Authorization:Platform $authorization" -H "X-BKREPO-UID: $user" -H "Content-Type: application/json" -d '{"name": "'$project'", "displayName": "'$project'", "description": ""}'

# 创建仓库
curl -X POST http://dev1.bkrepo.oa.com/api/repository/api/repo -H "Authorization:Platform $authorization" -H "X-BKREPO-UID: $user" -H "Content-Type: application/json" -d '{"projectId": "'$project'", "name": "pipeline", "type":"GENERIC", "category":"LOCAL", "public":false, "configuration": {"type":"local"}, "description":""}'
curl -X POST http://dev1.bkrepo.oa.com/api/repository/api/repo -H "Authorization:Platform $authorization" -H "X-BKREPO-UID: $user" -H "Content-Type: application/json" -d '{"projectId": "'$project'", "name": "custom", "type":"GENERIC", "category":"LOCAL", "public":false, "configuration": {"type":"local"}, "description":""}'
curl -X POST http://dev1.bkrepo.oa.com/api/repository/api/repo -H "Authorization:Platform $authorization" -H "X-BKREPO-UID: $user" -H "Content-Type: application/json" -d '{"projectId": "'$project'", "name": "report", "type":"GENERIC", "category":"LOCAL", "public":false, "configuration": {"type":"local"}, "description":""}'