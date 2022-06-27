import request from '@/utils/request'

const PREFIX_NOTIFY = '/opdata/api/notify'
const PREFIX_NOTIFY_CREDENTIALS = `${PREFIX_NOTIFY}/channel/credentials`

export function credentials() {
  return request({
    url: `${PREFIX_NOTIFY_CREDENTIALS}`,
    method: 'get'
  })
}

export function createCredential(credential) {
  return request({
    url: `${PREFIX_NOTIFY_CREDENTIALS}`,
    method: 'post',
    data: credential
  })
}

export function deleteCredential(name) {
  return request({
    url: `${PREFIX_NOTIFY_CREDENTIALS}/${name}`,
    method: 'delete'
  })
}

export function updateCredential(name, credential) {
  return request({
    url: `${PREFIX_NOTIFY_CREDENTIALS}/${name}`,
    method: 'put',
    data: credential
  })
}
