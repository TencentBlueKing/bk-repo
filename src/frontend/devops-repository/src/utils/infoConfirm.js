/**
 * 基于 <bk-info></bk-info>组件修改了部分样式
 */

import Vue from 'vue'

// 自定义目标
const customSubHeader = (opt) => {
    const vm = new Vue()
    const h = vm.$createElement
    const iconClass = opt.theme === 'danger' ? 'bk-icon icon-close-circle-shape' : 'bk-icon icon-exclamation-circle-shape'
    const themeColorMap = {
        warning: '#FFB549',
        danger: '#EA3736',
        primary: '#3a84ff'
    }

    return h('div', [
        h('div', {
            style: {
                display: 'flex',
                padding: '0 20px',
                marginBottom: '10px',
                lineHeight: '26px'
            }
        },
        [
            h('i', {
                attrs: {
                    class: iconClass
                },
                style: {
                    fontSize: '26px',
                    width: '26px',
                    height: '26px',
                    color: themeColorMap[opt.theme]
                }
            }),
            h('span', {
                style: {
                    fontWeight: '500',
                    fontSize: '14px',
                    color: '#081E40',
                    marginLeft: '10px'
                }
            }, opt.subTitle)
        ]),
        h('div', {
            style: {
                wordBreak: 'break-all',
                padding: '0 20px 0 60px',
                fontSize: '12px',
                color: '#8797aa'
            }
        }, opt.content)
    ])
}

export default customSubHeader
