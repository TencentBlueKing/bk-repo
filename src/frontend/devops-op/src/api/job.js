import request from '@/utils/request'

const PREFIX_SERVICES = '/job/service/job'

export function jobs() {
  return request({
    url: `${PREFIX_SERVICES}/detail`,
    method: 'get'
  })
}

export function update(name, status) {
  return request({
    url: `${PREFIX_SERVICES}/update/${name}/${status}`,
    method: 'get'
  })
}
