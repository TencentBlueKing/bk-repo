import request from '@/utils/request'

const PREFIX_SERVICES = '/opdata/api/internal-flow'

export function queryByLevel(level) {
  return request({
    url: `${PREFIX_SERVICES}/names/level/${level}`,
    method: 'get'
  })
}

export function queryRelationByName(name) {
  return request({
    url: `${PREFIX_SERVICES}/related/name/${name}`,
    method: 'get'
  })
}
