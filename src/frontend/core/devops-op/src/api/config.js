import request from '@/utils/request'

const PREFIX = '/opdata/api/config'

export function updateConfig(values, appName = '', profile = '') {
  const data = {
    appName,
    profile,
    values
  }
  return request({
    url: `${PREFIX}`,
    method: 'patch',
    data
  })
}

export function getConfig(key, appName = '', profile = '') {
  return request({
    url: `${PREFIX}`,
    method: 'get',
    params: {
      appName: appName,
      profile: profile,
      key: key
    }
  })
}
