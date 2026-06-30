import request from '@/utils/request'

const PREFIX = '/repository/api/client/upgrade/changelog'

export const CHANGELOG_STATUS = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED'
}

export function listChangelogs(option) {
  return request({
    url: `${PREFIX}/list`,
    method: 'post',
    data: option
  })
}

export function getChangelogById(id) {
  return request({
    url: `${PREFIX}/detail/${id}`,
    method: 'get'
  })
}

export function getChangelogByKey(productId, version) {
  return request({
    url: `${PREFIX}/detail`,
    method: 'get',
    params: { productId, version }
  })
}

export function upsertChangelog(data) {
  return request({
    url: `${PREFIX}/upsert`,
    method: 'post',
    data
  })
}

export function deleteChangelog(id) {
  return request({
    url: `${PREFIX}/${id}`,
    method: 'delete'
  })
}
