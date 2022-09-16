import request from '@/utils/request'

const PREFIX_SERVICES = '/job/api/job'

export function jobs() {
  return request({
    url: `${PREFIX_SERVICES}/detail`,
    method: 'get'
  })
}

export function update(name, enabled, running) {
  return request({
    url: `${PREFIX_SERVICES}/update/${name}`,
    method: 'put',
    params: {
      enabled: enabled,
      running: running
    }
  })
}
