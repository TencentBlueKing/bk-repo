import moment from 'moment'

const normalDateType = 'YYYY-MM-DD HH:mm:ss'

export function formatNormalDate(date) {
  return date != null ? moment(date).format(normalDateType) : null
}
