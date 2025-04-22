import request from '@/utils/request'

export const DEFAULT_PAGE_SIZE = 10
const PREFIX = '/opdata/api/fs-client'

export function queryFileSystemClient(body) {
  return request({
    url: `${PREFIX}/list/`,
    method: 'get',
    params: {
      pageNumber: body.pageNumber,
      pageSize: body.pageSize,
      projectId: body.projectId === '' ? null : body.projectId,
      repoName: body.repoName === '' ? null : body.repoName,
      userId: body.userId === '' ? null : body.userId,
      online: body.online === '' ? null : body.online,
      ip: body.ip === '' ? null : body.ip,
      version: body.version === '' ? null : body.version
    }
  })
}

export function queryDailyFileSystemClient(body, url = '') {
  return url === '' ? request({
    url: `${PREFIX}/daily/list/`,
    method: 'get',
    params: {
      pageNumber: body.pageNumber,
      pageSize: DEFAULT_PAGE_SIZE,
      projectId: body.projectId === '' ? null : body.projectId,
      repoName: body.repoName === '' ? null : body.repoName,
      ip: body.ip === '' ? null : body.ip,
      version: body.version === '' ? null : body.version,
      startTime: body.startTime === '' ? null : body.startTime,
      endTime: body.endTime === '' ? null : body.endTime,
      mountPoint: body.mountPoint === '' ? null : body.mountPoint,
      actions: body.actions
    }
  }) : request({
    url: `${PREFIX}/daily/list/` + url,
    method: 'get'
  })
}
