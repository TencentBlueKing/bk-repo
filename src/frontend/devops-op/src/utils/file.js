export function convertFileSize(size, unit = 'B') {
  const arr = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = arr.findIndex(v => v === unit)
  if (size > 1024) {
    return convertFileSize(size / 1024, arr[index + 1])
  } else {
    return `${index ? size.toFixed(2) : size}${unit}`
  }
}

function preZero(num) {
  num = Number(num)
  if (num < 10) {
    return '0' + num
  }
  return num
}

export function formatDate(ms) {
  if (!ms) return '--'
  const time = new Date(ms)
  return `${time.getFullYear()}-${
    preZero(time.getMonth() + 1)}-${
    preZero(time.getDate())} ${
    preZero(time.getHours())}:${
    preZero(time.getMinutes())}:${
    preZero(time.getSeconds())}`
}
