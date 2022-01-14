import router from '@/router'

const BASE_DIR = process.env.VUE_APP_BASE_DIR
const LOGIN_SERVICE_URL = process.env.VUE_APP_LOGIN_SERVICE_URL
const MODE_CONFIG = process.env.VUE_APP_MODE_CONFIG
const MODE_CONFIG_STAND_ALONE = 'standalone'
// const MODE_CONFIG_CI = 'ci'
// const MODE_CONFIG_SAAS = 'saas'

function getLoginUrl(redirectUrl) {
  const cUrl = location.origin + `/${BASE_DIR}` + (redirectUrl || '')
  if (/{+curl}+/i.test(LOGIN_SERVICE_URL)) {
    return LOGIN_SERVICE_URL.replace(/{+curl}+/i, encodeURIComponent(cUrl))
  } else if (/=%s/.test(LOGIN_SERVICE_URL)) {
    return LOGIN_SERVICE_URL.replace(/%s/, cUrl)
  } else {
    const loginUrl = new URL(LOGIN_SERVICE_URL)
    if (/=$/.test(loginUrl.search)) {
      return LOGIN_SERVICE_URL + cUrl
    } else {
      loginUrl.searchParams.append('c_url', cUrl)
      return loginUrl.href
    }
  }
}

export function toLoginPage(redirectUrl) {
  if (MODE_CONFIG === MODE_CONFIG_STAND_ALONE) {
    router.push(`/login?redirect=${redirectUrl}`)
  } else {
    window.postMessage({
      action: 'toggleLoginDialog'
    }, '*')
    location.href = getLoginUrl(redirectUrl)
  }
}
