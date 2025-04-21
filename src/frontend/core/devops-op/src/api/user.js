import request from '@/utils/request'
import { JSEncrypt } from 'jsencrypt'

const PREFIX_USER = '/auth/api/user'

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
