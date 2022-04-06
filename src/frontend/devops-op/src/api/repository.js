import request from '@/utils/request'

const PREFIX_REPOSITORY = '/repository/api/repo'

export function listRepositories(projectId) {
  return request({
    url: `${PREFIX_REPOSITORY}/list/${projectId}`,
    method: 'get'
  })
}
