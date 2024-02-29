export function convertFileSize(size, unit = 'B') {
  const arr = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = arr.findIndex(v => v === unit)
  if (size > 1024) {
    return convertFileSize(size / 1024, arr[index + 1])
  } else {
    return `${index ? size.toFixed(2) : size}${unit}`
  }
}

export function formatFileSize(size, unit = 'GB') {
  const arrays = ['B', 'KB', 'MB', 'GB', 'TB']
  let index
  for (let i = arrays.length - 1; i < arrays.length && i > -1; i--) {
    if (size.indexOf(arrays[i]) !== -1) {
      index = i
      break
    }
  }
  const sizeIndex = size.indexOf(arrays[index])
  const unitIndex = arrays.findIndex(i => i === unit)
  let temp = Number(size.substr(0, sizeIndex))
  if (unitIndex - index > 0) {
    for (let i = 0; i < unitIndex - index; i++) {
      temp = temp / 1024
    }
  }
  if (unitIndex - index < 0) {
    for (let i = 0; i < index - unitIndex; i++) {
      temp = temp * 1024
    }
  }
  return temp
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
