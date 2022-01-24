import request from '@/utils/request'

const PREFIX_NODE = '/repository/api/node'

export function searchNodes(projectId, repoName, fullPath, page, size) {
  return request({
    url: `${PREFIX_NODE}/page/${projectId}/${repoName}${fullPath}`,
    method: 'post',
    params: {
      pageNumber: page,
      pageSize: size,
      includeFolder: true,
      includeMetadata: true,
      deep: false,
      sort: true
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
    data: {
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
