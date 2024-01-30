import request from '@/utils/request'

export const DEFAULT_PAGE_SIZE = 10
const PREFIX = '/opdata/api/fs-client'

export function queryFileSystemClient(body) {
  return request({
    url: `${PREFIX}/list/`,
    method: 'get',
    params: {
      pageNumber: body.pageNumber,
      pageSize: DEFAULT_PAGE_SIZE,
      projectId: body.projectId === '' ? null : body.projectId,
      repoName: body.repoName === '' ? null : body.repoName,
      online: body.online === '' ? null : body.online,
      ip: body.ip === '' ? null : body.ip,
      version: body.version === '' ? null : body.version
    }
  })
}
