import request from '@/utils/request'

const PREFIX = '/opdata/api'

export function createMigrationBinding(data) {
  return request({
    url: `${PREFIX}/migration/binding`,
    method: 'post',
    data
  })
}

export function startMigration(data) {
  return request({
    url: `${PREFIX}/migration/start`,
    method: 'post',
    data
  })
}

export function completeMigrationDump(data) {
  return request({
    url: `${PREFIX}/migration/dump-complete`,
    method: 'post',
    data
  })
}

export function markMigrationReady(data) {
  return request({
    url: `${PREFIX}/migration/ready`,
    method: 'post',
    data
  })
}

export function enableMigrationDualWrite(data) {
  return request({
    url: `${PREFIX}/migration/dual-write`,
    method: 'post',
    data
  })
}

export function routeMigration(data) {
  return request({
    url: `${PREFIX}/migration/route`,
    method: 'post',
    data
  })
}

export function cleanupMigration(data) {
  return request({
    url: `${PREFIX}/migration/cleanup`,
    method: 'post',
    data
  })
}

export function rollbackMigration(data) {
  return request({
    url: `${PREFIX}/migration/rollback`,
    method: 'post',
    data
  })
}

export function getMigrationStatus(params) {
  return request({
    url: `${PREFIX}/migration/status`,
    method: 'get',
    params
  })
}

export function verifyAll() {
  return request({
    url: `${PREFIX}/migration/verify`,
    method: 'post'
  })
}

export function verifyProject(ruleName, projectId) {
  return request({
    url: `${PREFIX}/migration/verify/${ruleName}/${projectId}`,
    method: 'post'
  })
}

export function getRoutingReadiness() {
  return request({
    url: `${PREFIX}/routing/readiness`,
    method: 'get'
  })
}

export function getCompensationStats(ruleName) {
  return request({
    url: `${PREFIX}/compensation/stats`,
    method: 'get',
    params: { ruleName }
  })
}

export function getCompensationHealth(ruleName) {
  return request({
    url: `${PREFIX}/compensation/health/${ruleName}`,
    method: 'get'
  })
}
