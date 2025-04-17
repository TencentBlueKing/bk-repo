import request from '@/utils/request'

const PREFIX_NODE = '/repository/api/node'
const PREFIX_NODE_OPERATION = '/opdata/api/nodeOperation'
const PREFIX_SHARE = '/repository/api/share'
export const DEFAULT_PAGE_SIZE = 20
export const DEFAULT_NODE_OPERATION_PAGE_SIZE = 10

export function searchNodes(projectId, repoName, path, page, size, detail = false, deleted = null) {
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
  const rules = [
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
  ]
  const select = [
    'lastModifiedBy', 'lastModifiedDate', 'folder', 'name', 'size',
    'sha256', 'projectId', 'repoName', 'deleted', 'fullPath'
  ]
  if (detail) {
    select.push(...[
      'createdBy', 'createdDate', 'path', 'fullPath', 'expireDate', 'md5',
      'copyFromCredentialsKey', 'copyIntoCredentialsKey', 'metadata'
    ])
    rules.push({
      'field': 'deleted',
      'value': deleted || '',
      'operation': deleted ? 'EQ' : 'NULL'
    })
  }
  return request({
    url: `${PREFIX_NODE}/search`,
    method: 'post',
    data: {
      'page': { 'pageNumber': page, 'pageSize': size },
      'sort': { 'properties': ['folder', 'lastModifiedDate'], 'direction': 'DESC' },
      'rule': {
        'rules': rules,
        'relation': 'AND'
      },
      select: select
    }
  })
}

export function pageNodesBySha256(sha256, page = 1, size = DEFAULT_PAGE_SIZE) {
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

export function restoreNode(projectId, repoName, fullPath, deletedTimestamp) {
  const encodedPath = encodeURIComponent(fullPath.substring(1))
  return request({
    url: `${PREFIX_NODE}/restore/${projectId}/${repoName}/${encodedPath}`,
    method: 'post',
    params: {
      deletedId: deletedTimestamp,
      conflictStrategy: 'SKIP'
    }
  })
}

export function deleteNode(projectId, repoName, fullPath) {
  const encodedPath = encodeURIComponent(fullPath.substring(1))
  return request({
    url: `${PREFIX_NODE}/delete/${projectId}/${repoName}/${encodedPath}`,
    method: 'delete'
  })
}

export function queryEmptyFolder(projectId, repoName, parentPath) {
  return request({
    url: `${PREFIX_NODE_OPERATION}/emptyFolders/${projectId}/${repoName}`,
    method: 'get',
    timeout: 600000,
    params: {
      parentFolder: parentPath
    }
  })
}

export function deleteEmptyFolder(projectId, repoName, parentPath) {
  return request({
    url: `${PREFIX_NODE_OPERATION}/emptyFolders/${projectId}/${repoName}`,
    timeout: 600000,
    method: 'delete',
    params: {
      parentFolder: parentPath
    }
  })
}

export function statisticalFirstLevelFolder(projectId, repoName, pageNumber) {
  return request({
    url: `${PREFIX_NODE_OPERATION}/firstLevelFolder/${projectId}/${repoName}`,
    timeout: 600000,
    method: 'get',
    params: {
      pageNumber: pageNumber,
      pageSize: DEFAULT_NODE_OPERATION_PAGE_SIZE
    }
  })
}

export function copyNode(node) {
  return request({
    url: `${PREFIX_NODE}/copy`,
    method: 'post',
    data: node
  })
}

export function renameNode(node) {
  return request({
    url: `${PREFIX_NODE}/rename`,
    method: 'post',
    data: node
  })
}

export function moveNode(node) {
  return request({
    url: `${PREFIX_NODE}/move`,
    method: 'post',
    data: node
  })
}

export function shareNode(node) {
  return request({
    url: `${PREFIX_SHARE}/${node.projectId}/${node.repoName}/${node.artifactUri}`,
    method: 'post',
    data: node
  })
}
