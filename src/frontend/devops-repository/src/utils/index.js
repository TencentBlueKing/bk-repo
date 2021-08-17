/**
 *  转换文件大小
 */
export function convertFileSize (size, unit = 'B') {
    const arr = ['B', 'KB', 'MB', 'GB', 'TB']
    const index = arr.findIndex(v => v === unit)
    if (size > 1024) {
        return convertFileSize(size / 1024, arr[index + 1])
    } else {
        return `${index ? size.toFixed(2) : size}${unit}`
    }
}

const DEFAULT_TIME_INTERVAL = 1000
export function debounce (fn, interval = DEFAULT_TIME_INTERVAL) {
    let timer = null

    return (...args) => {
        clearTimeout(timer)
        timer = setTimeout(() => {
            clearTimeout(timer)
            return fn(...args)
        }, interval)
    }
}

export function throttle (func, interval = DEFAULT_TIME_INTERVAL) {
    let lastRun = Date.now()
    return (...args) => {
        if (Date.now() - lastRun > interval) {
            func(...args)
            lastRun = Date.now()
        }
    }
}

export function throttleMessage (fn, delay = 1000) {
    let lastTime = 0
    return function (messageBody) {
        // 设置默认配置
        const message = {
            offsetY: 80,
            ellipsisLine: 0,
            ...messageBody
        }
        if (message.theme !== 'error') {
            fn(message)
            return
        }
        if (['property', 'defined'].find(ignore => !message.message || message.message.indexOf(ignore) !== -1)) {
            throw new Error(message.message)
        }
        const now = +new Date()
        if (lastTime + delay > now) return
        fn(message)
        lastTime = now
    }
}

function prezero (num) {
    num = Number(num)
    if (num < 10) {
        return '0' + num
    }
    return num
}

export function formatDate (ms) {
    if (!ms) return '--'
    const time = new Date(ms)
    return `${time.getFullYear()}-${
        prezero(time.getMonth() + 1)}-${
        prezero(time.getDate())} ${
        prezero(time.getHours())}:${
        prezero(time.getMinutes())}:${
        prezero(time.getSeconds())}`
}
