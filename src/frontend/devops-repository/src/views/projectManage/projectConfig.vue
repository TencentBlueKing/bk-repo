<template>
    <div class="project-detail-container">
        <bk-tab class="project-detail-tab" type="unborder-card" :active.sync="tabName">
            <bk-tab-panel name="basic" label="基础信息">
                <bk-form class="ml10 mr10" :label-width="75">
                    <bk-form-item label="项目标识">
                        <span>{{ currentProject.id }}</span>
                    </bk-form-item>
                    <bk-form-item label="项目名称">
                        <span>{{ currentProject.name }}</span>
                    </bk-form-item>
                    <bk-form-item label="项目描述">
                        <span>{{ currentProject.description }}</span>
                    </bk-form-item>
                    <bk-form-item v-if="$route.name === 'projectManage'">
                        <bk-button theme="primary" @click="$emit('edit-basic', currentProject)">修改</bk-button>
                    </bk-form-item>
                </bk-form>
            </bk-tab-panel>
            <bk-tab-panel v-for="tab in [manage, user, role]" :key="tab.name" :name="tab.name" :label="tab.name">
                <div class="flex-align-center">
                    <bk-select class="w250 select-user"
                        v-model="tab.add"
                        multiple
                        searchable
                        placeholder="请选择用户"
                        :enable-virtual-scroll="selectList(tab).length > 3000"
                        :list="selectList(tab)">
                        <bk-option v-for="option in selectList(tab)"
                            :key="option.id"
                            :id="option.id"
                            :name="option.name">
                        </bk-option>
                    </bk-select>
                    <bk-button icon="plus" theme="primary" class="ml10" @click="confirmHandler(tab, 'add')"><span class="mr5">{{ $t('add') }}</span></bk-button>
                    <bk-button v-show="tab.delete.length" theme="warning" class="ml10" @click="confirmHandler(tab, 'delete')"><span class="mr5">批量删除</span></bk-button>
                </div>
                <bk-table
                    class="mt10"
                    :data="tab.items"
                    height="calc(100% - 104px)"
                    :outer-border="false"
                    :row-border="false"
                    size="small"
                    @select="list => {
                        tab.delete = list
                    }"
                    @select-all="list => {
                        tab.delete = list
                    }">
                    <template #empty><empty-data style="margin-top:100px;"></empty-data></template>
                    <bk-table-column type="selection" width="60"></bk-table-column>
                    <bk-table-column :label="tab.name"><template #default="{ row }">
                        {{ (userList[row] && userList[row].name) || (roleList[row] && roleList[row].name) || row }}
                    </template></bk-table-column>
                </bk-table>
            </bk-tab-panel>
        </bk-tab>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'projectConfig',
        props: {
            project: {
                type: Object
            }
        },
        data () {
            return {
                tabName: 'basic',
                manage: {
                    id: 'manage',
                    loading: false,
                    name: '项目管理员',
                    type: 'user',
                    items: [],
                    add: [],
                    delete: []
                },
                user: {
                    id: 'user',
                    loading: false,
                    name: '项目用户',
                    type: 'user',
                    items: [],
                    add: [],
                    delete: []
                },
                role: {
                    id: 'role',
                    loading: false,
                    name: '项目用户组',
                    type: 'role',
                    items: [],
                    add: [],
                    delete: []
                },
                roleList: {}
            }
        },
        computed: {
            ...mapState(['userList', 'projectList']),
            projectId () {
                return this.$route.params.projectId
            },
            currentProject () {
                return this.project || this.projectList.find(project => project.id === this.projectId) || {}
            }
        },
        watch: {
            currentProject () {
                this.initProjectConfig()
            }
        },
        created () {
            this.currentProject.id && this.initProjectConfig()
            this.getRoleList().then(res => {
                this.roleList = res.reduce((target, item) => {
                    target[item.id] = item
                    return target
                }, {})
            })
        },
        methods: {
            ...mapActions([
                'getRoleList',
                'getProjectPermission',
                'setUserPermission',
                'setRolePermission'
            ]),
            initProjectConfig () {
                this.getProjectPermission({ projectId: this.currentProject.id }).then(data => {
                    const manage = data.find(p => p.permName === 'project_manage_permission') || {}
                    const view = data.find(p => p.permName === 'project_view_permission') || {}
                    this.manage = {
                        ...this.manage,
                        id: manage.id,
                        items: manage.users
                    }
                    this.user = {
                        ...this.user,
                        id: view.id,
                        items: view.users
                    }
                    this.role = {
                        ...this.role,
                        id: view.id,
                        items: view.roles
                    }
                })
            },
            selectList (tab) {
                return Object.values(tab.type === 'role' ? this.roleList : this.userList)
                    .filter(v => v.id !== 'anonymous')
                    .filter(v => !~tab.items.findIndex(w => w === v.id))
            },
            confirmHandler (tab, type) {
                const fn = {
                    user: this.setUserPermission,
                    role: this.setRolePermission
                }[tab.type]
                const key = {
                    user: 'userId',
                    role: 'rId'
                }[tab.type]
                const value = {
                    add: [...tab.items, ...tab.add],
                    delete: tab.items.filter(v => !tab.delete.find(w => w === v))
                }[type]
                if (tab.loading || !tab[type].length) return
                tab.loading = true
                fn({
                    body: {
                        permissionId: tab.id,
                        [key]: value
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (type === 'add' ? this.$t('add') : this.$t('delete')) + this.$t('success')
                    })
                    this.initProjectConfig()
                    tab[type] = []
                }).finally(() => {
                    tab.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.project-detail-container {
    height: 100%;
    background-color: white;
    .project-detail-tab {
        height: 100%;
        ::v-deep .bk-tab-section {
            height: calc(100% - 50px);
            .bk-tab-content {
                height: 100%;
            }
        }
    }
}
</style>
