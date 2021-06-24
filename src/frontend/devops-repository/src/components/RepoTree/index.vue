<template>
    <ul class="repo-tree-list">
        <li class="repo-tree-item" :key="item.roadMap" v-for="item of treeList">
            <div v-if="deepCount" class="line-dashed" :class="{ 'more': sortable && list.length > 20 }" :style="{
                'border-width': '0 1px 0 0',
                'margin-left': (20 * deepCount + 5) + 'px',
                'height': '100%',
                'margin-top': '-15px'
            }"></div>
            <div class="repo-tree-title hover-btn"
                :title="item.name"
                :class="{ 'selected': selectedNode.roadMap === item.roadMap }"
                :style="{ 'padding-left': 20 * (deepCount + 1) + 'px' }"
                @click.stop="itemClickHandler(item)">
                <div class="line-dashed" :style="{
                    'border-width': openList.includes(item.roadMap) ? '0 1px 0 0' : '0',
                    'margin-left': (20 * deepCount + 25) + 'px',
                    'height': 'calc(100% - 45px)',
                    'margin-top': '25px'
                }"></div>
                <div v-if="deepCount" class="line-dashed" :style="{
                    'border-width': '1px 0 0',
                    'margin-left': '-13px',
                    'width': '15px'
                }"></div>
                <i v-if="item.loading" class="mr5 loading"></i>
                <i v-else class="mr5 devops-icon" @click.stop="iconClickHandler(item)"
                    :class="openList.includes(item.roadMap) ? 'icon-down-shape' : 'icon-right-shape'"></i>
                <icon class="mr5" size="14" :name="openList.includes(item.roadMap) ? 'folder-open' : 'folder'"></icon>
                <div class="node-text" :title="item.name">
                    {{ item.name }}
                </div>
            </div>
            <CollapseTransition>
                <template v-if="item.children && item.children.length">
                    <repo-tree
                        v-show="openList.includes(item.roadMap)"
                        :list.sync="item.children"
                        :sortable="sortable"
                        :deep-count="deepCount + 1"
                        :selected-node="selectedNode"
                        :important-search="importantSearch"
                        :open-list="openList"
                        @icon-click="iconClickHandler"
                        @item-click="itemClickHandler">
                    </repo-tree>
                </template>
            </CollapseTransition>
        </li>
    </ul>
</template>

<script>
    import CollapseTransition from './collapse-transition.js'
    export default {
        name: 'repo-tree',
        components: {
            CollapseTransition
        },
        props: {
            list: {
                type: Array,
                default: () => []
            },
            deepCount: {
                type: Number,
                default: 0
            },
            importantSearch: {
                type: String,
                default: ''
            },
            selectedNode: {
                type: Object,
                default: () => {}
            },
            openList: {
                type: Array,
                default: () => []
            },
            sortable: {
                type: Boolean,
                default: false
            }
        },
        computed: {
            treeList () {
                const list = this.list.filter(v => v.folder)
                if (this.sortable) {
                    const reg = new RegExp(`^${this.selectedNode.roadMap},[0-9]+$`)
                    const isSearch = reg.test(list[0].roadMap) && this.importantSearch
                    return list.sort((a, b) => {
                        if (~this.selectedNode.roadMap.indexOf(a.roadMap)) return -1
                        // 选中项的子项应用搜索
                        if (isSearch) {
                            const weightA = a.name.indexOf(this.importantSearch)
                            const weightB = b.name.indexOf(this.importantSearch)
                            if (~weightA && ~weightB) return weightA - weightB
                            else return weightB - weightA
                        }
                        return 0
                    }).slice(0, 20)
                } else {
                    return list
                }
            }
        },
        methods: {
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
            }
        }
    }
</script>

<style lang="scss">
@import '@/scss/conf';
.repo-tree-item {
    position: relative;
    color: $fontBoldColor;
    font-size: 12px;
    .line-dashed {
        position: absolute;
        border-color: $borderLightColor;
        border-style: dashed;
        z-index: 1;
    }
    &:last-child > .line-dashed {
        height: 30px!important;
        &.more:after {
            content: '...';
            position: absolute;
            top: 30px;
            left: 30px;
            font-size: 20px;
        }
    }
    .repo-tree-title {
        position: relative;
        height: 30px;
        display: flex;
        align-items: center;
        .loading {
            display: inline-block;
            width: 12px;
            height: 12px;
            border: 1px solid;
            border-right-color: transparent;
            border-radius: 50%;
            animation: loading 1s linear infinite;
        }
        .devops-icon {
            color: $fontColor;
        }
        .node-text {
            max-width: 150px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            em {
                font-style: normal;
                color:#e4393c;
                &:hover {
                    color: $primaryColor;
                }
            }
        }
        &.selected {
            background-color: $primaryLightColor;
            color: $primaryColor;
        }
    }
}
</style>
