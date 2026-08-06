import moment from 'moment'

const normalDateType = 'YYYY-MM-DD HH:mm:ss'

export function formatNormalDate(date) {
  return date != null ? moment(date).format(normalDateType) : null
}

export function formatApiDateTime(time) {
  if (!time) return ''
  if (Array.isArray(time) && time.length >= 3) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = time
    return moment({
      year,
      month: month - 1,
      date: day,
      hour,
      minute,
      second
    }).format(normalDateType)
  }
  let date = time
  if (typeof time === 'string' && /^[0-9]+$/.test(time)) {
    date = parseInt(time, 10)
  }
  if (typeof date === 'number' && date.toString().length === 10) {
    date = date * 1000
  }
  const formatted = moment(date)
  return formatted.isValid() ? formatted.format(normalDateType) : String(time)
}

const units = ['毫秒', '秒', '分', '小时', '天']
const multiples = ['1000', '60', '60', '24', '1']

// 转换成xx天xx小时xx分xx秒xx毫秒
export function convertTime(time) {
  let temp = time
  let index = 0
  let target = ''
  while (index < 5) {
    if (temp % multiples[index] > 0) {
      target = temp % multiples[index] + units[index] + target
    }
    if (index === 4) {
      target = parseInt(temp / multiples[index]) + units[index] + target
      break
    }
    temp = parseInt(temp / multiples[index])
    if (temp > 1) {
      index++
    } else {
      break
    }
  }
  return target
}

// 只有一个单位，保留2位小数，比如xx天、xx小时
export function roughCalculateTime(time) {
  let temp = time
  let index = 0
  let target = ''
  while (index < 5) {
    if (index === 4) {
      target = (temp / multiples[index]).toFixed(2) + units[index]
      break
    }
    temp = temp / multiples[index]
    if (temp > 1) {
      index++
    } else {
      target = (temp * multiples[index]).toFixed(2) + units[index]
      break
    }
  }
  return target
}
