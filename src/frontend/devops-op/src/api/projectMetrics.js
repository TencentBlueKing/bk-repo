import request from '@/utils/request'

export const DEFAULT_PAGE_SIZE = 10
const PREFIX = '/opdata/api/project/metrics'

export function queryProjectMetrics(projectId, pageNumber) {
  return request({
    url: `${PREFIX}/list`,
    method: 'get',
    params: {
      pageNumber: pageNumber,
      pageSize: DEFAULT_PAGE_SIZE,
      projectId: projectId
    }
  })
}
