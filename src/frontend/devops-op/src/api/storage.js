import request from '@/utils/request'
import { updateConfig } from '@/api/config'

const PREFIX_STORAGE = '/repository/api/storage'
const PREFIX_STORAGE_CREDENTIALS = `${PREFIX_STORAGE}/credentials`
const STORAGE_CONFIG_PREFIX = 'storage'
const STORAGE_CACHE_CONFIG_PREFIX = 'cache'
export const STORAGE_TYPE_FILESYSTEM = 'filesystem'
export const STORAGE_TYPE_INNER_COS = 'innercos'
export const STORAGE_TYPE_HDFS = 'hdfs'
export const STORAGE_TYPE_S3 = 's3'

export function credentials() {
  return request({
    url: `${PREFIX_STORAGE_CREDENTIALS}`,
    method: 'get'
  })
}

export function defaultCredential() {
  return request({
    url: `${PREFIX_STORAGE_CREDENTIALS}/default`,
    method: 'get'
  })
}

export function createCredential(credential) {
  if (credential.upload && (!credential.upload.location || credential.upload.location.length === 0)) {
    credential.upload.location = undefined
  }
  const createReq = {
    key: credential.key,
    credentials: credential,
    region: credential.region ? credential.region : ''
  }
  return request({
    url: `${PREFIX_STORAGE_CREDENTIALS}`,
    method: 'post',
    data: createReq
  })
}

export function updateCredential(key, credential, defaultCredential = false) {
  if (defaultCredential) {
    const expireDaysKey = `${STORAGE_CONFIG_PREFIX}.${credential.type}.${STORAGE_CACHE_CONFIG_PREFIX}.expireDays`
    const loadCacheFirstKey = `${STORAGE_CONFIG_PREFIX}.${credential.type}.${STORAGE_CACHE_CONFIG_PREFIX}.loadCacheFirst`
    const values = {
      [expireDaysKey]: credential.cache.expireDays,
      [loadCacheFirstKey]: credential.cache.loadCacheFirst
    }
    return updateConfig(values)
  } else {
    const data = {
      loadCacheFirst: credential.cache.loadCacheFirst,
      expireDays: credential.cache.expireDays
    }
    return request({
      url: `${PREFIX_STORAGE_CREDENTIALS}/${key}`,
      method: 'put',
      data
    })
  }
}

export function deleteCredential(key) {
  return request({
    url: `${PREFIX_STORAGE_CREDENTIALS}/${key}`,
    method: 'delete'
  })
}
