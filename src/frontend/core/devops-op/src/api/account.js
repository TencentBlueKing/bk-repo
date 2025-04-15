import request from '@/utils/request'

const PREFIX_SERVICES = '/auth/api/account'

export function list() {
  return request({
    url: `${PREFIX_SERVICES}/list`,
    method: 'get'
  })
}

export function update(account) {
  return request({
    url: `${PREFIX_SERVICES}/update`,
    method: 'put',
    data: account
  })
}

export function create(account) {
  return request({
    url: `${PREFIX_SERVICES}/create`,
    method: 'post',
    data: account
  })
}

export function deleteAccount(appid) {
  return request({
    url: `${PREFIX_SERVICES}/delete/${appid}`,
    method: 'delete'
  })
}

export function deleteKey(appid, accessKey) {
  return request({
    url: `${PREFIX_SERVICES}/credential/${appid}/${accessKey}`,
    method: 'delete'
  })
}

export function createKey(appid, authTypes) {
  return request({
    url: `${PREFIX_SERVICES}/credential/${appid}`,
    method: 'post',
    params: {
      type: authTypes
    }
  })
}

export function updateKey(appid, accessKey, status) {
  return request({
    url: `${PREFIX_SERVICES}/credential/${appid}/${accessKey}/${status}`,
    method: 'put'
  })
}

export function keyLists(appid) {
  return request({
    url: `${PREFIX_SERVICES}/credential/list/${appid}`,
    method: 'get'
  })
}
