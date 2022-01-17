import request from '@/utils/request'

const PREFIX_USER = '/auth/api/user'

export function login(formData) {
  return request({
    url: `${PREFIX_USER}/login`,
    method: 'post',
    data: formData,
    config: { headers: { 'Content-Type': 'multipart/form-data' }}
  })
}

export function userInfo() {
  return request({
    url: `${PREFIX_USER}/info`,
    method: 'get'
  })
}

export function userInfoById(userId) {
  return request({
    url: `${PREFIX_USER}/userinfo/${userId}`,
    method: 'get'
  })
}

export function userDetail(userId) {
  return request({
    url: `${PREFIX_USER}/detail/${userId}`,
    method: 'get'
  })
}
