import request from '@/utils/request'

const PREFIX_NODE = '/repository/api/node'

export function searchNodes(projectId, repoName, path, page, size, detail = false) {
  const dir = path.endsWith('/')
  let pathRule
  if (dir) {
    pathRule = {
      'field': 'path',
      'value': path,
      'operation': 'EQ'
    }
  } else {
    pathRule = {
      'field': 'fullPath',
      'value': path,
      'operation': 'EQ'
    }
  }
  const select = [
    'lastModifiedBy', 'lastModifiedDate', 'folder', 'name', 'size',
    'sha256', 'projectId', 'repoName', 'deleted', 'fullPath'
  ]
  if (detail) {
    select.push(...[
      'createdBy', 'createdDate', 'path', 'fullPath', 'expireDate', 'md5',
      'copyFromCredentialsKey', 'copyIntoCredentialsKey', 'metadata'
    ])
  }
  return request({
    url: `${PREFIX_NODE}/search`,
    method: 'post',
    data: {
      'page': { 'pageNumber': page, 'pageSize': size },
      'sort': { 'properties': ['folder', 'lastModifiedDate'], 'direction': 'DESC' },
      'rule': {
        'rules': [
          {
            'field': 'projectId',
            'value': projectId,
            'operation': 'EQ'
          },
          {
            'field': 'repoName',
            'value': repoName,
            'operation': 'EQ'
          },
          pathRule
        ],
        'relation': 'AND'
      },
      select: select
    }
  })
}

export function pageNodesBySha256(sha256, page = 1, size = 10) {
  return request({
    url: `${PREFIX_NODE}/page`,
    method: 'get',
    params: {
      sha256,
      pageNumber: page,
      pageSize: size,
      includeMetadata: true
    }
  })
}

export function restoreNode(projectId, repoName, path, deletedTimestamp) {
  return request({
    url: `${PREFIX_NODE}/restore/${projectId}/${repoName}/${path}`,
    method: 'post',
    params: {
      deletedId: deletedTimestamp,
      conflictStrategy: 'SKIP'
    }
  })
}

export function deleteNode(projectId, repoName, fullPath) {
  return request({
    url: `${PREFIX_NODE}/delete/${projectId}/${repoName}${fullPath}`,
    method: 'delete'
  })
}
