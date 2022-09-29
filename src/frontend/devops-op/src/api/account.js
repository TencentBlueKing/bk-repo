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
