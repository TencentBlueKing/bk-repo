import request from '@/utils/request'

const PREFIX_SCAN = '/scanner/api/scan'
const PREFIX_SCANNER = '/scanner/api/scanners'

export const SCANNER_TYPE_ARROWHEAD = 'arrowhead'
export const SCANNER_TYPE_TRIVY = 'trivy'
export const SCANNER_TYPE_DEPENDENCY_CHECK = 'DependencyCheck'

export function createScanner(scanner) {
  return request({
    url: `${PREFIX_SCANNER}`,
    method: 'post',
    data: scanner
  })
}

export function deleteScanner(name) {
  return request({
    url: `${PREFIX_SCANNER}/${name}`,
    method: 'delete'
  })
}

export function updateScanner(scanner) {
  return request({
    url: `${PREFIX_SCANNER}/${scanner.name}`,
    method: 'put',
    data: scanner
  })
}

export function scanners() {
  return request({
    url: `${PREFIX_SCANNER}`,
    method: 'get'
  })
}

export function scan(scanner, projectId, repoName, path) {
  return request({
    url: `${PREFIX_SCAN}`,
    method: 'post',
    data: {
      scanner: scanner,
      rule: {
        relation: 'AND',
        rules: [
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
          {
            'field': 'fullPath',
            'value': path,
            'operation': 'PREFIX'
          }
        ]
      }
    }
  })
}
