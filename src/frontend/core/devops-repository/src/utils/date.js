import moment from 'moment'

// 获取当前时间前几天的date
export function before (days) {
    return moment().subtract(days, 'days').toDate()
}

// 获取传输时间的零点零分零秒的date
export function zeroTime (date) {
    if (date instanceof Date) {
        return moment([date.getFullYear(), date.getMonth(), date.getDate()]).toDate()
    } else if (date instanceof moment) {
        return moment([date.year(), date.month(), date.date()]).toDate()
    } else {
        return moment([moment().year(), moment().month(), moment().date()]).toDate()
    }
}

// 获取当前时间前几年的date
export function beforeYears (years) {
    return moment().subtract(years, 'years').toDate()
}

// 获取当前时间前几月的date
export function beforeMonths (months) {
    return moment().subtract(months, 'months').toDate()
}
