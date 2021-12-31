<template>
    <div class="virtual-tree" @scroll="scrollTree($event)">
        <ul class="repo-tree-list">
            <li class="repo-tree-item" :key="item.roadMap" v-for="item of treeList">
                <div class="repo-tree-title hover-btn"
                    :title="item.name"
                    :class="{ 'selected': selectedNode.roadMap === item.roadMap }"
                    :style="{ 'padding-left': 20 * computedDepth(item) + 10 + 'px' }"
                    @click.stop="itemClickHandler(item)">
                    <i v-if="item.loading" class="mr5 loading spin-icon"></i>
                    <i v-else-if="!item.leaf" class="mr5 devops-icon" @click.stop="iconClickHandler(item)"
                        :class="openList.includes(item.roadMap) ? 'icon-down-shape' : 'icon-right-shape'"></i>
                    <slot name="icon" :item="item" :open-list="openList">
                        <Icon class="mr5" size="14" :name="openList.includes(item.roadMap) ? 'folder-open' : 'folder'" />
                    </slot>
                    <slot name="text" :item="item">
                        <div class="node-text" :title="item.name" v-html="importantTransform(item.name)"></div>
                    </slot>
                </div>
            </li>
        </ul>
        <div class="tree-phantom" :style="`height:${totalHeight}px;`"></div>
    </div>
</template>

<script>
    import { throttle } from '@repository/utils'
    export default {
        name: 'repo-tree',
        props: {
            tree: Array, // roadMap、name、children、leaf
            importantSearch: {
                type: String,
                default: ''
            },
            selectedNode: {
                type: Object,
                default: () => ({
                    roadMap: '',
                    name: ''
                })
            },
            openList: {
                type: Array,
                default: () => []
            }
        },
        data () {
            return {
                resizeFn: null,
                size: 0,
                start: 0
            }
        },
        computed: {
            flattenTree () {
                const flatNodes = []
                const flatten = treeData => {
                    treeData.forEach(treeNode => {
                        flatNodes.push(treeNode)
                        // 过滤未展开文件夹
                        this.openList.includes(treeNode.roadMap) && flatten(treeNode.children || [])
                    })
                }
                flatten(this.tree)
                return flatNodes
            },
            treeList () {
                return this.flattenTree.slice(this.start, this.start + this.size)
            },
            totalHeight () {
                return (this.flattenTree.length + 1) * 40
            }
        },
        watch: {
            'selectedNode.roadMap' () {
                this.calculateScrollTop(new RegExp(`^${this.selectedNode.name}$`))
            },
            importantSearch (val) {
                val && this.calculateScrollTop(val)
            }
        },
        mounted () {
            this.resizeFn = throttle(this.computedSize)
            this.computedSize()
            window.addEventListener('resize', this.resizeFn)
        },
        beforeDestroy () {
            window.removeEventListener('resize', this.resizeFn)
        },
        methods: {
            scrollTree () {
                this.start = Math.floor(this.$el.scrollTop / 40)
            },
            calculateScrollTop (keyword) {
                let fn = () => false
                if (typeof keyword === 'string') {
                    fn = v => v.name.includes(keyword)
                } else if (keyword instanceof RegExp) {
                    fn = v => keyword.test(v.name)
                }
                const index = this.flattenTree.findIndex(fn)
                if (!~index) return
                if (index < this.start) {
                    this.$el.scrollTop = index * 40
                } else if (index > (this.start + this.size - 1)) {
                    this.$el.scrollTop = (index - this.size + 1) * 40
                }
            },
            computedSize () {
                setTimeout(() => {
                    const height = this.$el.getBoundingClientRect().height
                    this.size = Math.ceil(height / 40)
                // dialog缩放动画.4s
                }, 400)
            },
            computedDepth (node) {
                return node.roadMap.split(',').length - 1
            },
            /**
             *  点击icon的回调函数
             */
            iconClickHandler (item) {
                this.$emit('icon-click', item)
            },
            /**
             *  单击folder的回调函数
             */
            itemClickHandler (item) {
                this.$emit('item-click', item)
            },
            importantTransform (name) {
                if (!this.importantSearch) return name
                const normalText = name.split(this.importantSearch)
                return normalText.reduce((a, b) => {
                    return a + `<em>${this.importantSearch}</em>` + b
                })
            }
        }
    }
</script>

<style lang="scss">
.virtual-tree {
    position: relative;
    display: flex;
    align-items: flex-start;
    height: 100%;
    overflow: auto;
    .repo-tree-list {
        position: sticky;
        top: 0;
        width: 100%;
        height: 100%;
        overflow: hidden;
    }
}
.repo-tree-item {
    position: relative;
    color: var(--fontPrimaryColor);
    .line-dashed {
        position: absolute;
        border-color: var(--borderColor);
        border-style: dashed;
        z-index: 1;
    }
    &:last-child > .line-dashed {
        height: 40px!important;
    }
    .repo-tree-title {
        position: relative;
        height: 40px;
        display: flex;
        align-items: center;
        .loading {
            display: inline-block;
            width: 12px;
            height: 12px;
            border: 1px solid;
            border-right-color: transparent;
            border-radius: 50%;
            z-index: 1;
        }
        .devops-icon {
            color: var(--fontSubsidiaryColor);
            z-index: 1;
        }
        .node-text {
            max-width: 150px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            em {
                font-style: normal;
                font-weight: bold;
                background-color: var(--warningColor);
            }
        }
        &:hover {
            background-color: var(--bgLightColor);
            .devops-icon {
                color: var(--primaryColor);
            }
        }
        &.selected {
            background-color: var(--bgHoverLighterColor);
            color: var(--primaryColor);
            .devops-icon {
                color: var(--primaryColor);
            }
        }
    }
}
</style>
