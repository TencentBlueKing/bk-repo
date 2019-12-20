#!/bin/bash

curl -XPOST -H "X-BKREPO-UID: admin" -H "Content-Type: application/json" http://bkrepo.oa.com/api/repository/service/repo -d '{"operator":"admin", "projectId":"'${1}'","name":"pipeline","type":"GENERIC","category":"LOCAL","public":false,"description":"","configuration":{"type":"local"}}'

curl -XPOST -H "X-BKREPO-UID: admin" -H "Content-Type: application/json" http://bkrepo.oa.com/api/repository/service/repo -d '{"operator":"admin", "projectId":"'${1}'","name":"custom","type":"GENERIC","category":"LOCAL","public":false,"description":"","configuration":{"type":"local"}}}'

curl -XPOST -H "X-BKREPO-UID: admin" -H "Content-Type: application/json" http://bkrepo.oa.com/api/repository/service/repo -d '{"operator":"admin", "projectId":"'${1}'","name":"report","type":"GENERIC","category":"LOCAL","public":false,"description":"","configuration":{"type":"local"}}}'

