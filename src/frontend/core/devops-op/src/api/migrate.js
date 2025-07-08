import request from '@/utils/request'

const PREFIX_SERVICES = '/job/api/job/migrate'

export function queryMigrateTask(data) {
  return request({
    url: `${PREFIX_SERVICES}/tasks`,
    method: 'get',
    params: data
  })
}

export function createMigrateTask(data) {
  return request({
    url: `${PREFIX_SERVICES}/`,
    method: 'post',
    data: data
  })
}
