import createLocale from '@locale'
/**
 *  转换文件大小
 */
export function convertFileSize (size, unit = 'B') {
    const arr = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
    const index = arr.findIndex(v => v === unit)
    if (size > 1024) {
        if (arr[index + 1]) {
            return convertFileSize(size / 1024, arr[index + 1])
        } else {
            return `${index ? size.toFixed(2) : size}${unit}`
        }
    } else {
        return `${index ? size.toFixed(2) : size}${unit}`
    }
}

const DEFAULT_TIME_INTERVAL = 1000
export function debounce (fn, interval = DEFAULT_TIME_INTERVAL) {
    let timer = null

    return (...args) => {
        clearTimeout(timer)
        timer = setTimeout(() => fn(...args), interval)
    }
}

export function throttle (func, interval = DEFAULT_TIME_INTERVAL) {
    let lastRun = Date.now()
    let finalRun = null
    return (...args) => {
        clearTimeout(finalRun)
        finalRun = setTimeout(() => func(...args), interval)
        if (Date.now() - lastRun > interval) {
            func(...args)
            lastRun = Date.now()
        }
    }
}

export function throttleMessage (fn, delay = 1000) {
    let lastTime = 0
    return messageBody => {
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
        const now = +new Date()
        if (lastTime + delay > now) return
        fn(message)
        lastTime = now
    }
}

function prezero (num) {
    num = Number(num)
    if (num < 10) {
        return `0${num}`
    }
    return num
}

export function formatDate (ms) {
    if (!ms) return ms || '/'
    const time = new Date(ms)
    return `${time.getFullYear()}-${
        prezero(time.getMonth() + 1)}-${
        prezero(time.getDate())} ${
        prezero(time.getHours())}:${
        prezero(time.getMinutes())}:${
        prezero(time.getSeconds())}`
}

// 加载先于main.js,初次渲染Vue.prototype.$ajax.defaults为空，二次渲染于main.js，此时Vue.prototype.$ajax.defaults不为空，此时添加报文头
const { i18n } = createLocale(require.context('@locale/repository/', false, /\.json$/))

const durationMap = {
    s: { label: i18n.t('cron.second'), deno: 60, next: 'm' },
    m: { label: i18n.t('cron.minute'), deno: 60, next: 'h' },
    h: { label: i18n.t('cron.hour'), deno: 24, next: 'd' },
    d: { label: i18n.t('cron.day'), deno: 365 }
}
export function formatDuration (duration, unit = 's', target = []) {
    if (!duration) return duration || '/'
    duration = Math.floor(Number(duration))
    const { label, deno, next } = durationMap[unit]
    const current = duration % deno ? `${duration % deno}${label}` : ''
    duration = Math.floor(duration / deno)
    if (!duration) {
        return [current, ...target].slice(0, 2).join('') || i18n.t('lessOneSecondTip')
    }
    return formatDuration(duration, next, [current, ...target])
}

// 数字三位分隔
export function segmentNumberThree (num) {
    if (!num || !Number(num)) return num
    let [int, dot] = Number(num).toString().split('.')
    int = Number(int).toLocaleString()
    dot = dot ? `.${dot}` : ''
    return `${int}${dot}`
}

export function copyToClipboard (text) {
    // navigator clipboard 需要https等安全上下文
    // if (navigator.clipboard && window.isSecureContext) {
    //     // iframe情况下也用不了，visit: https://www.cnblogs.com/accordion/p/14784047.html
    //     return navigator.clipboard.writeText(text)
    // } else {
    const textArea = document.createElement('textarea')
    textArea.style.position = 'absolute'
    textArea.style.width = 0
    textArea.style.height = 0
    textArea.style.left = '-10px'
    textArea.style.top = '-10px'
    document.body.appendChild(textArea)

    textArea.value = text
    textArea.focus()
    textArea.select()
    const result = document.execCommand('copy')
    textArea.remove()
    return result ? Promise.resolve() : Promise.reject(new Error())
    // }
}
