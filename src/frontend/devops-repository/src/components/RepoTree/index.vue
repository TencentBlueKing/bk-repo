<template>
    <ul class="repo-tree-list">
        <li class="repo-tree-item" :key="item.roadMap" v-for="item of treeList">
            <div v-if="deepCount" class="line-dashed" :style="{
                'border-right': '1px dashed',
                'margin-left': (20 * deepCount + 5) + 'px',
                'height': '100%',
                'margin-top': '-20px'
            }"></div>
            <div class="repo-tree-title hover-btn"
                :title="item.name"
                :class="{ 'selected': selectedNode.roadMap === item.roadMap }"
                :style="{ 'padding-left': 20 * (deepCount + 1) + 'px' }"
                @click.stop="itemClickHandler(item)">
                <div class="line-dashed" :style="{
                    'border-right': openList.includes(item.roadMap) ? '1px dashed' : '0 none',
                    'margin-left': (20 * deepCount + 25) + 'px',
                    'height': 'calc(100% - 45px)',
                    'margin-top': '25px'
                }"></div>
                <div v-if="deepCount" class="line-dashed" :style="{
                    'border-top': '1px dashed',
                    'margin-left': '-13px',
                    'width': '15px'
                }"></div>
                <i v-if="item.loading" class="mr5 loading"></i>
                <i v-else class="mr5 devops-icon" @click.stop="iconClickHandler(item)"
                    :class="openList.includes(item.roadMap) ? 'icon-down-shape' : 'icon-right-shape'"></i>
                <icon class="mr5" size="18" :name="openList.includes(item.roadMap) ? 'folder-open' : 'folder'"></icon>
                <div class="node-text"
                    :class="{ 'title-bold': importantSearch && item.name.toLowerCase().includes(importantSearch.toLowerCase()) }"
                    :title="item.name">
                    {{ item.name }}
                </div>
            </div>
            <CollapseTransition>
                <template v-if="item.children && item.children.length">
                    <repo-tree
                        v-show="openList.includes(item.roadMap)"
                        :list.sync="item.children"
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
                default: []
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
                default: {}
            },
            openList: {
                type: Array,
                default: []
            }
        },
        computed: {
            treeList () {
                return this.list.filter(v => v.folder)
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
li:last-child>.line-dashed {
    height: 40px!important;
}
.repo-tree-item {
    position: relative;
    color: $fontWeightColor;
    .line-dashed {
        position: absolute;
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
            animation: loading 1s linear infinite;
        }
        .node-text {
            max-width: 150px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        .title-bold {
            font-weight: bolder;
            background-color: #fafb0b;
        }
        &.selected {
            background-color: $primaryLightColor;
            color: $primaryColor;
        }
    }
}
</style>
