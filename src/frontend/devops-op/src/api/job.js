import request from '@/utils/request'

const PREFIX_SERVICES = '/job/service/job'

export function jobs() {
  return request({
    url: `${PREFIX_SERVICES}/detail`,
    method: 'get'
  })
}
