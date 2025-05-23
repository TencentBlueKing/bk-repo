<template>
    <div class="project-detail-container">
        <bk-tab class="project-detail-tab page-tab" type="unborder-card" :active.sync="tabName">
            <bk-tab-panel name="basic" :label="$t('baseInfo')">
                <bk-form class="ml10 mr10" :label-width="75">
                    <bk-form-item :label="$t('projectId')">
                        <span>{{ currentProject.id }}</span>
                    </bk-form-item>
                    <bk-form-item :label="$t('projectName')">
                        <span>{{ currentProject.name }}</span>
                    </bk-form-item>
                    <bk-form-item :label="$t('projectDescription')">
                        <span>{{ currentProject.description }}</span>
                    </bk-form-item>
                    <bk-form-item>
                        <bk-button theme="primary" @click="showProjectDialog">{{ $t('edit') }}</bk-button>
                    </bk-form-item>
                </bk-form>
            </bk-tab-panel>
            <!-- <bk-tab-panel v-for="tab in [manage, user, role]" :key="tab.name" :name="tab.name" :label="tab.name"> -->
            <bk-tab-panel v-for="tab in [manage, user]" :key="tab.name" :name="tab.name" :label="tab.name">
                <div class="flex-align-center">
                    <bk-select class="w250 select-user"
                        v-model="tab.add"
                        multiple
                        searchable
                        :placeholder="$t('selectUserMsg')"
                        :enable-virtual-scroll="selectList(tab).length > 3000"
                        :list="selectList(tab)">
                        <bk-option v-for="option in selectList(tab)"
                            :key="option.id"
                            :id="option.id"
                            :name="option.name">
                        </bk-option>
                    </bk-select>
                    <bk-button :disabled="!tab.add.length" icon="plus" theme="primary" class="ml10" @click="confirmHandler(tab, 'add')">{{ $t('add') }}</bk-button>
                    <bk-button :disabled="!tab.delete.length" theme="default" class="ml10" @click="confirmHandler(tab, 'delete')">
                        {{ $t('batchRemove')}}</bk-button>
                </div>
                <bk-table
                    class="mt10"
                    :data="tab.items"
                    height="calc(100% - 40px)"
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
                    <bk-table-column :label="tab.name" width="1600"><template #default="{ row }">
                        {{ (userList[row] && userList[row].name) || (roleList[row] && roleList[row].name) || row }}
                    </template></bk-table-column>
                </bk-table>
            </bk-tab-panel>
        </bk-tab>
        <project-info-dialog ref="projectInfoDialog"></project-info-dialog>
    </div>
</template>
<script>
    import projectInfoDialog from './projectInfoDialog'
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'projectConfig',
        components: { projectInfoDialog },
        data () {
            return {
                tabName: 'basic',
                manage: {
                    id: 'manage',
                    loading: false,
                    name: this.$t('projectManager'),
                    type: 'user',
                    items: [],
                    add: [],
                    delete: []
                },
                user: {
                    id: 'user',
                    loading: false,
                    name: this.$t('projectUser'),
                    type: 'user',
                    items: [],
                    add: [],
                    delete: []
                },
                role: {
                    id: 'role',
                    loading: false,
                    name: this.$t('projectUserGroup'),
                    type: 'role',
                    items: [],
                    add: [],
                    delete: []
                },
                roleList: {},
                admins: [],
                users: [],
                currentProjectUser: []
            }
        },
        computed: {
            ...mapState(['userList', 'projectList']),
            projectId () {
                return this.$route.query.projectId || this.$route.params.projectId
            },
            currentProject () {
                return this.projectList.find(project => project.id === this.projectId) || {}
            }
        },
        watch: {
            currentProject () {
                this.initProjectConfig()
            }
        },
        beforeRouteEnter (to, from, next) {
            const breadcrumb = to.meta.breadcrumb
            if (to.query.projectId) {
                breadcrumb.splice(0, breadcrumb.length, { name: 'projectManage', label: to.query.projectId }, { name: 'projectConfig', label: 'projectConfig' })
            } else {
                breadcrumb.splice(0, breadcrumb.length, { name: 'projectConfig', label: 'projectConfig' })
            }
            next()
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
                'setRolePermission',
                'getProjectUserList'
            ]),
            initProjectConfig () {
                this.getProjectUserList({ projectId: this.currentProject.id }).then(data => {
                    const res = data.reduce((target, item) => {
                        target[item.userId] = {
                            id: item.userId,
                            name: item.name
                        }
                        return target
                    }, {})
                    this.currentProjectUser = {
                        ...res,
                        anonymous: {
                            id: 'anonymous',
                            name: '/'
                        }
                    }
                })
                this.getProjectPermission({ projectId: this.currentProject.id }).then(data => {
                    const manage = data.find(p => p.permName === 'project_manage_permission') || {}
                    const view = data.find(p => p.permName === 'project_view_permission') || {}
                    this.admins = manage.users
                    this.users = view.users
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
                const usersWithoutAnonymous = Object.values(this.currentProjectUser).filter(v => v.id !== 'anonymous')
                let final = usersWithoutAnonymous
                if ((tab.items instanceof Array) && tab.items.length !== 0) {
                    final = usersWithoutAnonymous.filter(v => !~tab.items.findIndex(w => w === v.id))
                }
                if (tab.name === this.$t('projectUser')) {
                    if (this.admins.length !== 0 && final.length !== 0) {
                        final = final.filter(v => !~this.admins.findIndex(w => w === v.id))
                    }
                }
                if (tab.name === this.$t('projectManager')) {
                    if (this.users.length !== 0 && final.length !== 0) {
                        final = final.filter(v => !~this.users.findIndex(w => w === v.id))
                    }
                }
                return final
            },
            confirmHandler (tab, type) {
                if (tab.loading || !tab[type].length) return
                const key = { user: 'userId', role: 'rId' }[tab.type]
                const value = {
                    add: [...tab.items, ...tab.add],
                    delete: tab.items.filter(v => !tab.delete.find(w => w === v))
                }[type]
                const deleteName = tab.delete.map(v => this.userList[v]?.name || this.roleList[v]?.name || v)

                const confirmFn = () => {
                    tab.loading = true
                    return ({ user: this.setUserPermission, role: this.setRolePermission })[tab.type]({
                        body: {
                            permissionId: tab.id,
                            projectId: this.currentProject.id,
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

                type === 'add'
                    ? confirmFn()
                    : this.$confirm({
                        theme: 'danger',
                        message: this.$t('removeConfirm') + `${deleteName} ?`,
                        confirmFn
                    })
            },
            showProjectDialog () {
                const { id = '', name = '', description = '' } = this.currentProject
                this.$refs.projectInfoDialog.setData({
                    show: true,
                    loading: false,
                    add: !id,
                    id,
                    name,
                    description
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
            height: calc(100% - 60px);
            .bk-tab-content {
                height: 100%;
            }
        }
    }
}
</style>
