import router from '@/router'

// const BASE_DIR = process.env.VUE_APP_BASE_DIR
var PAAS_SERVICE_URL = '__BK_HTTP_SCHEMA__://__BK_REPO_PAAS_FQDN__'
var LOGIN_SERVICE_URL = /^https?/.test(PAAS_SERVICE_URL)
  ? PAAS_SERVICE_URL + '/login/'
  : '__BK_REPO_PAAS_LOGIN_URL__'
export const MODE_CONFIG = '__BK_REPO_DEPLOY_MODE__' || 'standalone'
export const MODE_CONFIG_STAND_ALONE = 'standalone'
export const MODE_CONFIG_CI = 'ci'
export const MODE_CONFIG_SAAS = 'saas'

function getLoginUrl(redirectUrl) {
  const cUrl = location.origin + (redirectUrl || '')
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
  if (MODE_CONFIG === MODE_CONFIG_CI || MODE_CONFIG === MODE_CONFIG_SAAS) {
    window.postMessage({
      action: 'toggleLoginDialog'
    }, '*')
    location.href = getLoginUrl(redirectUrl)
  } else {
    router.push(`/login?redirect=${redirectUrl}`)
  }
}
