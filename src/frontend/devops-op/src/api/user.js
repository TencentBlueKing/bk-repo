import request from '@/utils/request'

const prefix = 'auth/api'

export function login(data) {
  return request({
    url: `/${prefix}/user/login`,
    method: 'post',
    data,
    config: { headers: { 'Content-Type': 'multipart/form-data' }}
  })
}

export function userInfo() {
  return request({
    url: `/${prefix}/user/info`,
    method: 'get'
  })
}

export function userInfoById(userId) {
  return request({
    url: `/${prefix}/user/userinfo/${userId}`,
    method: 'get'
  })
}

export function userDetail(userId) {
  return request({
    url: `/${prefix}/user/detail/${userId}`,
    method: 'get'
  })
}
