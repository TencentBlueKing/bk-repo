import Vue from 'vue'
import SvgIcon from '@/components/SvgIcon'// svg component

// register globally
Vue.component('SvgIcon', SvgIcon)

// Vite 方式导入所有 SVG 图标并创建 sprite
const svgModules = import.meta.glob('./svg/*.svg', { eager: true, query: '?raw', import: 'default' })

// 创建 SVG sprite
const svgSprite = Object.entries(svgModules)
  .map(([path, content]) => {
    // 从文件路径提取图标名称
    const fileName = path.replace(/^.*\/([^/]+)\.svg$/, '$1')

    // 移除 XML 声明、DOCTYPE 和注释
    let svgContent = content
      .replace(/<\?xml[^?]*\?>/gi, '')
      .replace(/<!DOCTYPE[^>]*>/gi, '')
      .replace(/<!--[\s\S]*?-->/g, '')
      .trim()

    // 将 svg 标签转换为 symbol，并移除可能干扰的属性
    svgContent = svgContent
      .replace(/<svg[^>]*>/, (match) => {
        // 提取 viewBox 属性
        const viewBoxMatch = match.match(/viewBox=["']([^"']*)["']/)

        let viewBox = ''
        if (viewBoxMatch) {
          viewBox = ` viewBox="${viewBoxMatch[1]}"`
        } else {
          // 如果没有 viewBox，尝试从 width 和 height 生成
          const widthMatch = match.match(/width=["']([^"']*)["']/)
          const heightMatch = match.match(/height=["']([^"']*)["']/)
          if (widthMatch && heightMatch) {
            const width = parseFloat(widthMatch[1])
            const height = parseFloat(heightMatch[1])
            viewBox = ` viewBox="0 0 ${width} ${height}"`
          }
        }

        return `<symbol id="icon-${fileName}"${viewBox}>`
      })
      .replace(/<\/svg>/, '</symbol>')

    return svgContent
  })
  .join('')

// 创建并注入 SVG sprite 到页面
if (typeof document !== 'undefined') {
  const svgContainer = document.createElement('div')
  svgContainer.style.cssText = 'position: absolute; width: 0; height: 0; overflow: hidden; visibility: hidden;'
  svgContainer.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg">${svgSprite}</svg>`
  document.body.insertBefore(svgContainer, document.body.firstChild)
}

