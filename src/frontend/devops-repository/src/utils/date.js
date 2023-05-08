import moment from 'moment'

// 获取当前时间前几天的date
export function before (days) {
    return moment().subtract(days, 'days').toDate()
}

// 获取传输时间的零点零分零秒的date
export function zeroTime (date) {
    return moment([date.year(), date.month(), date.date()]).toDate()
}
