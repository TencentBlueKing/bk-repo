<template>
    <div class="store-sort-body">
        <div class="sort-head">
            <div class="sort-index"></div>
            <div class="sort-name">{{$t('name')}}</div>
            <div class="sort-category">{{$t('storeTypes')}}</div>
            <div v-if="!disabled" class="sort-operation">{{$t('operation')}}</div>
        </div>
        <draggable :disabled="disabled" class="sort-content" v-if="list.length" :list="list" :options="{ animation: 200,sort: true }">
            <div class="sort-item" :class="{ 'sort-cursor': !disabled }" v-for="(item,index) in list" :key="item.name + Math.random()">
                <div class="sort-index flex-center"><Icon name="drag" size="16" /></div>
                <div class="sort-name">{{item.name}}</div>
                <div class="sort-category flex-align-center">
                    <Icon class="mr5" :name="item.category.toLowerCase() + '-store'" size="16" />
                    <span>{{$t(item.category.toLowerCase() + 'Store') }}</span>
                </div>
                <div v-if="!disabled" class="sort-operation flex-align-center">
                    <Icon class="hover-btn" size="24" name="icon-delete" @click.native.stop="deleteStore(index)" />
                </div>
            </div>
        </draggable>
    </div>
  
</template>
<script>
    import draggable from 'vuedraggable'
    export default {
        name: 'storeSort',
        components: { draggable },
        props: {
            sortList: {
                type: Array,
                required: true
            },
            // 是否禁用，此时禁用拖拽排序及删除已选仓库(不显示操作所在列)
            disabled: {
                type: Boolean,
                default: false
            }
        },
        data () {
            return {
                loading: false,
                list: this.sortList
            }
        },
        methods: {
            deleteStore (index) {
                this.list.splice(index, 1)
                this.$emit('update', this.list)
            }
        }
    }
</script>
<style lang="scss" scoped>
.store-sort-body{
    overflow: hidden;
}
    .sort-item,
    .sort-head {
        display: flex;
        align-items: center;
        height: 40px;
        line-height: 40px;
        border-bottom: 1px solid var(--borderColor);
        .sort-index {
            flex-basis: 50px;
        }
        .sort-category {
            flex:2;
        }
        .sort-name {
            flex:3;
        }
        .sort-operation {
            flex:1;
        }
    }
    .sort-head {
        color: var(--fontSubsidiaryColor);
        background-color: var(--bgColor);
    }
    .sort-cursor{
        cursor: move;
    }
    .sort-content {
        max-height:200px;
        overflow-y: auto
    }
</style>
