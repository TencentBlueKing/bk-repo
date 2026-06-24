import request from '@/utils/request'
import { JSEncrypt } from 'jsencrypt'

const PREFIX_USER = '/auth/api/user'
// 用户信息相关接口改走 opdata，opdata 内部会通过 Feign 调 auth，
// auth 不可用时降级返回固定 admin，保证运营端在 auth 故障时仍可访问。
const PREFIX_USER_OP = '/opdata/api/user'

export function rsa() {
  return request({
    url: `${PREFIX_USER}/rsa`,
    method: 'get'
  })
}

export function login(formData) {
  return rsa().then(res => {
    const encryptor = new JSEncrypt()
    encryptor.setPublicKey(res.data)
    const encryptedFormData = new FormData()
    encryptedFormData.append('uid', formData.get('uid'))
    encryptedFormData.append('token', encryptor.encrypt(formData.get('token')))
    return encryptedFormData
  }).then(encryptedFormData => {
    return request({
      url: `${PREFIX_USER}/login`,
      method: 'post',
      data: encryptedFormData,
      config: { headers: { 'Content-Type': 'multipart/form-data' }}
    })
  })
}

export function userInfo() {
  return request({
    url: `${PREFIX_USER_OP}/info`,
    method: 'get'
  })
}

export function userInfoById(userId) {
  return request({
    url: `${PREFIX_USER_OP}/userinfo/${userId}`,
    method: 'get'
  })
}

export function userDetail(userId) {
  return request({
    url: `${PREFIX_USER}/detail/${userId}`,
    method: 'get'
  })
}
