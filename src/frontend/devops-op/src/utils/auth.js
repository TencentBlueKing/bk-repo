import Cookies from 'js-cookie'

export const TOKEN_KEY = 'bkrepo_ticket'
export const BK_TICKET = 'bk_ticket'
export const BK_UID = 'bk_uid'

export function getBkTicket() {
  return Cookies.get(BK_TICKET)
}

export function getBkUid() {
  return Cookies.get(BK_UID)
}

export function getToken() {
  return Cookies.get(TOKEN_KEY)
}

export function setToken(token) {
  return Cookies.set(TOKEN_KEY, token)
}

export function removeToken() {
  return Cookies.remove(TOKEN_KEY)
}
