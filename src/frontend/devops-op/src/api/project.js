import request from '@/utils/request'

const PREFIX_PROJECT = '/repository/api/project'

export function listProjects() {
  return request({
    url: `${PREFIX_PROJECT}/list`,
    method: 'get'
  })
}

export function searchProjects(namePrefix, page = 1, size = 20) {
  namePrefix = namePrefix || null
  return request({
    url: `${PREFIX_PROJECT}/search`,
    method: 'get',
    params: {
      namePrefix: namePrefix,
      pageNumber: page,
      pageSize: size
    }
  })
}
