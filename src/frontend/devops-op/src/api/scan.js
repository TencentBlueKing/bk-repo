import request from '@/utils/request'

const PREFIX_SCAN = '/scanner/api/scan'
const PREFIX_SCANNER = '/scanner/api/scanners'

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
