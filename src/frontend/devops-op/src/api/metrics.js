import request from '@/utils/request'
const PREFIX_METRICS = '/opdata/api/fileSystem'
export const DEFAULT_PAGE_SIZE = 10

export function querySelectOption() {
  return request({
    url: `${PREFIX_METRICS}/storage/metrics/list`,
    method: 'get'
  })
}

export function queryDetail(path, pageNumber) {
  return request({
    url: `${PREFIX_METRICS}/storage/metricsDetail`,
    method: 'get',
    params: {
      rootPath: path,
      pageNumber: pageNumber,
      pageSize: DEFAULT_PAGE_SIZE
    }
  })
}

export function queryMetrics(pageNumber) {
  return request({
    url: `${PREFIX_METRICS}/storage/metrics`,
    method: 'get',
    params: {
      pageNumber: pageNumber,
      pageSize: DEFAULT_PAGE_SIZE
    }
  })
}
