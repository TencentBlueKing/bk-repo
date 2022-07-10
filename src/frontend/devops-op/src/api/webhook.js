import request from '@/utils/request'

const PREFIX_WEBHOOK = '/webhook/api/webhook'
const PREFIX_WEBHOOK_LOG = '/webhook/api/log'
export const DEFAULT_PAGE_SIZE = 20

export function listWebHook(associationType, associationId) {
  if (associationType === undefined || associationType.length === 0) {
    associationType = 'SYSTEM'
  }
  return request({
    url: `${PREFIX_WEBHOOK}/list`,
    method: 'get',
    params: {
      associationType,
      associationId
    }
  })
}

export function createWebhook(url, headers, triggers, associationType, associationId, resourceKeyPattern) {
  return request({
    url: `${PREFIX_WEBHOOK}/create`,
    method: 'post',
    data: {
      'url': url,
      'headers': headers,
      'triggers': triggers,
      'associationType': associationType,
      'associationId': associationId,
      'resourceKeyPattern': resourceKeyPattern
    }
  })
}

export function updateWebhook(id, url, headers, triggers, resourceKeyPattern) {
  return request({
    url: `${PREFIX_WEBHOOK}/update`,
    method: 'put',
    data: {
      'id': id,
      'url': url,
      'headers': headers,
      'triggers': triggers,
      'resourceKeyPattern': resourceKeyPattern
    }
  })
}

export function deleteWebhook(id) {
  return request({
    url: `${PREFIX_WEBHOOK}/delete/${id}`,
    method: 'delete'
  })
}

export function listWebhookLog(id, startDate = null, endDate = null, status = null) {
  return request({
    url: `${PREFIX_WEBHOOK_LOG}/list/${id}`,
    method: 'get',
    params: {
      startDate,
      endDate,
      status
    }
  })
}
