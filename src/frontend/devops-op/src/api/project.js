import request from '@/utils/request'

const PREFIX_PROJECT = '/repository/api/project'

export function listProjects() {
  return request({
    url: `${PREFIX_PROJECT}/list`,
    method: 'get'
  })
}

