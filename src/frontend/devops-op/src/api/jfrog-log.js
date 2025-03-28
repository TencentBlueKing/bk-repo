import request from '@/utils/request'

const PREFIX_SERVICES = '/opdata/api/server/logs'

export function getLogConfig() {
  return request({
    url: `${PREFIX_SERVICES}/config`,
    method: 'get'
  })
}

export function getLog(logName, nodeId, startPosition) {
  return request({
    url: `${PREFIX_SERVICES}/data`,
    method: 'get',
    params: {
      nodeId: nodeId === '' ? null : nodeId,
      logFileName: logName,
      startPosition: startPosition
    }
  })
}
