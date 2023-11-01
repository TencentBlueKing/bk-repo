import request from '@/utils/request'

const PREFIX_SERVICES = '/opdata/api'
export const DEFAULT_PAGE_SIZE = 10

export function page(num, size, startTime, endTime, userId) {
  return request({
    url: `${PREFIX_SERVICES}/log/page`,
    method: 'get',
    params: {
      startTime: startTime,
      endTime: endTime,
      operator: userId,
      pageNumber: num,
      pageSize: size
    }
  })
}
