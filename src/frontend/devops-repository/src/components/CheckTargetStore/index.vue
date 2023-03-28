<template>
    <canway-dialog
        v-model="show"
        width="520"
        height-num="410"
        :title="title"
        @cancel="cancel">
        <div class="check-target-body">
            <bk-table
                ref="checkStoreTableRef"
                class="mt10"
                :data="storeList"
                height="250px"
                :outer-border="false"
                :row-border="false"
                size="small"
                v-bkloading="{ isLoading }"
                @scroll-end="onScrolled"
                @select="onSelect"
                @select-all="onSelectAll"
            >
                <template #empty>
                    <empty-data :is-loading="isLoading"></empty-data>
                </template>
                <bk-table-column type="selection" width="40"></bk-table-column>
                <bk-table-column :label="$t('repoName')" show-overflow-tooltip>
                    <template #default="{ row }">
                        <span>{{replaceRepoName(row.name)}}</span>
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('storeTypes')" width="150">
                    <template #default="{ row }">
                        <Icon class="mr5" :name="(row.category.toLowerCase() || 'local') + '-store'" size="16" />
                        <span>{{ $t((row.category.toLowerCase() || 'local') + 'Store')}}</span>
                    </template>
                </bk-table-column>
            </bk-table>
        </div>
        <template #footer>
            <bk-button @click="cancel">{{$t('cancel')}}</bk-button>
            <bk-button class="ml10" :loading="loading" theme="primary" @click="confirm">{{$t('confirm')}}</bk-button>
        </template>
    </canway-dialog>
</template>
<script>
    import { mapActions } from 'vuex'
    import { cloneDeep } from 'lodash'
    export default {
        name: 'checkTargetStore',
        components: { },
        props: {
            title: {
                type: String,
                default () {
                    return this.$t('select') + this.$t('space') + this.$t('storageStore')
                }
            },
            // 当前选择的制品类型
            repoType: {
                type: String,
                default: ''
            },
            // 默认选中的数据
            checkList: {
                type: Array,
                default: () => []
            }
        },
        data () {
            return {
                show: false,
                isLoading: false,
                hasNext: true,
                storeList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 10,
                    limitList: [10, 20, 40]
                },
                newCheckedList: [] // 表格中选中的数据
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId || ''
            }
        },
        watch: {
            show (val) {
                if (val) {
                    // 当打开弹窗时将父组件选中的数据赋值，然后在下方设置选中
                    this.newCheckedList = cloneDeep(this.checkList)
                    this.handlerPaginationChange()
                } else {
                    this.storeList = []
                }
            }
        },
        methods: {
            ...mapActions([
                'getRepoList'
            ]),
            // 滚动条划到底部的方法
            onScrolled () {
                this.hasNext && this.handlerPaginationChange({ current: this.pagination.current + 1 })
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}, load) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getStoreList()
            },
            // 获取列表数据
            getStoreList () {
                this.isLoading = true
                this.getRepoList({
                    projectId: this.projectId,
                    current: this.pagination.current,
                    limit: this.pagination.limit,
                    type: this.repoType,
                    category: 'LOCAL,REMOTE'
                }).then((res) => {
                    this.storeList = this.storeList.concat(res.records)
                    if (res.records.length < this.pagination.limit) {
                        this.hasNext = false
                    } else {
                        this.hasNext = true
                    }
                    // 因为是滚动分页，所以每次重新加载数据之后都要重新设置选中状态
                    this.setCheckData()
                }).finally(() => {
                    this.isLoading = false
                })
            },
            // 表格中改变某一行的选中状态
            onSelect (selection, row) {
                if (selection.map(item => item.name).includes(row.name)) {
                    // 此时表明是勾选操作
                    // 当已选中的数组中没有当前元素时才需要设置选中，如果已经存在了(全选操作导致的)则不需要处理
                    !(this.newCheckedList.find(v => v.name === row.name)) && this.newCheckedList.push(row)
                } else {
                    // 取消勾选,需要先找到之前选中的元素所在的位置，然后从选中的数组中取消
                    const index = this.newCheckedList.findIndex(item => item.name === row.name)
                    this.newCheckedList.splice(index, 1)
                }
            },
            // 全选操作回调
            onSelectAll (selection) {
                if (selection.length > 0) {
                    // 设置全选
                    selection.forEach((row) => {
                        this.onSelect(selection, row)
                    })
                } else {
                    // 取消全选
                    this.storeList.forEach((row) => {
                        this.onSelect([], row)
                    })
                }
            },
            // 打开弹窗时设置初始化选中的数据
            setCheckData () {
                this.$nextTick(() => {
                    if (this.newCheckedList.length > 0) {
                        // 获取默认选中数据
                        const selected = this.storeList.filter(store => this.newCheckedList.map(item => item.name).includes(store.name))
                        selected.forEach(item => {
                            this.$refs.checkStoreTableRef && this.$refs.checkStoreTableRef.toggleRowSelection(item, true)
                        })
                    }
                })
            },
            cancel () {
                this.show = false
            },
            confirm () {
                this.$emit('checkedTarget', this.newCheckedList)
                this.show = false
            }
        }
    }
</script>
<style lang="scss" scoped>
// 选择存储库的dialog弹窗上方不需要留那么多的空白部分，影响下方表格内容展示
::v-deep .canway-dialog-header{
    margin-bottom: 0;
}
</style>
