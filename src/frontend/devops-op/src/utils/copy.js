export function copyToClipboard(text) {
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
}
