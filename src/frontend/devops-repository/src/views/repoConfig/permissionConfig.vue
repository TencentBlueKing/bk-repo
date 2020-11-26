<template>
    <bk-collapse class="permission-config-container" v-model="activeName" v-bkloading="{ isLoading }">
        <bk-collapse-item v-for="section in [admin, user, viewer]" :key="section.name" :name="section.name">
            <header class="section-header">
                <div class="flex-align-center">
                    <icon class="mr10" size="24" :name="section.icon"></icon>
                    <span>{{ section.title }}</span>
                    <span class="mr10 permission-actions">（{{ getActions(section.actions.data) }}）</span>
                    <i v-if="section !== admin" class="devops-icon icon-edit hover-btn" @click.stop="editActionsDialogHandler(section)"></i>
                </div>
            </header>
            <div slot="content" class="section-main">
                <template v-for="part in ['users', 'roles', ...(section !== admin ? [] : [])]">
                    <header :key="part + 'header'" class="section-sub-title flex-align-center">
                        <span>{{section[part].title}}</span>
                        <i class="ml10 devops-icon icon-plus-square hover-btn" @click="section[part].showAddArea = !section[part].showAddArea"></i>
                    </header>
                    <div v-show="section[part].showAddArea" :key="part + 'operation'" class="mt10 flex-align-center">
                        <template v-if="part === 'departments'">
                            <bk-select
                                style="min-width: 250px"
                                searchable
                                multiple
                                v-model="section[part].addList"
                                :remote-method="(keyword) => $refs[`${section.name}Tree`][0].filter(keyword)"
                                :display-tag="true"
                                :tag-fixed-height="false"
                                :show-empty="false"
                                @tab-remove="({ id }) => $refs[`${section.name}Tree`][0].setChecked(id, { emitEvent: true, checked: false })"
                                @clear="$refs[`${section.name}Tree`][0].removeChecked({ emitEvent: false })">
                                <bk-big-tree
                                    :ref="`${section.name}Tree`"
                                    :data="departmentList"
                                    show-checkbox
                                    :check-strictly="false"
                                    show-link-line
                                    :default-checked-nodes="section[part].addList"
                                    @check-change="ids => section[part].addList = [...ids]">
                                </bk-big-tree>
                            </bk-select>
                        </template>
                        <template v-else>
                            <bk-tag-input
                                style="min-width: 250px"
                                v-model="section[part].addList"
                                :list="Object.values({
                                    users: userList,
                                    roles: roleList
                                }[part])
                                    .filter(v => !section[part]
                                        .data
                                        .find(w => w === v.id)
                                    )"
                                trigger="focus"
                                allow-create
                                has-delete-icon>
                            </bk-tag-input>
                        </template>
                        <bk-button v-if="section[part].addList.length" class="ml20" :loading="section.loading" theme="primary" @click="submit('add', part, section)">{{$t('add')}}</bk-button>
                    </div>
                    <div :key="part + 'data'">
                        <div class="section-sub-main mt10">
                            <div class="permission-tag" v-for="tag in (section[part].data.filter(v => !section[part].deleteList.find(w => w === v)))" :key="tag">
                                {{ getName(part, tag) }}
                                <i class="devops-icon icon-close-circle-shape" @click="section[part].deleteList.push(tag)"></i>
                            </div>
                        </div>
                        <div v-if="section[part].deleteList && section[part].deleteList.length">
                            <bk-button :loading="section.loading" theme="primary" @click="submit('delete', part, section)">{{$t('save')}}</bk-button>
                            <bk-button class="ml10" theme="default" @click="section[part].deleteList = []">{{$t('cancel')}}</bk-button>
                        </div>
                    </div>
                </template>
            </div>
        </bk-collapse-item>
        <bk-dialog
            v-model="editActionsDialog.show"
            :title="editActionsDialog.title"
            width="410"
            :close-icon="false"
            :quick-close="false"
            :draggable="false">
            <bk-checkbox-group v-model="editActionsDialog.actions">
                <bk-checkbox v-for="action in actionList" :key="action.id" class="m20" :value="action.id">{{ action.name }}</bk-checkbox>
            </bk-checkbox-group>
            <div slot="footer">
                <bk-button :loading="loading" theme="primary" @click.stop.prevent="handleActionPermission">{{$t('submit')}}</bk-button>
                <bk-button theme="default" @click.stop="editActionsDialog.show = false">{{$t('cancel')}}</bk-button>
            </div>
        </bk-dialog>
    </bk-collapse>
</template>
<script>
    import { mapActions } from 'vuex'
    export default {
        name: 'permissionConfig',
        data () {
            return {
                isLoading: false,
                loading: false,
                activeName: ['admin', 'user', 'viewer'],
                editActionsDialog: {
                    show: false,
                    id: '',
                    name: '',
                    title: '',
                    actions: []
                },
                admin: {
                    name: 'admin',
                    loading: false,
                    title: '管理者',
                    icon: 'perm-controller',
                    id: '',
                    actions: {
                        data: []
                    },
                    users: {
                        title: '用户',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    roles: {
                        title: '用户组',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    departments: {
                        title: '组织',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    }
                },
                user: {
                    name: 'user',
                    loading: false,
                    title: '使用者',
                    icon: 'perm-user',
                    id: '',
                    actions: {
                        data: []
                    },
                    users: {
                        title: '用户',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    roles: {
                        title: '用户组',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    departments: {
                        title: '组织',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    }
                },
                viewer: {
                    name: 'viewer',
                    loading: false,
                    title: '查看者',
                    icon: 'perm-viewer',
                    id: '',
                    actions: {
                        data: []
                    },
                    users: {
                        title: '用户',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    roles: {
                        title: '用户组',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    departments: {
                        title: '组织',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    }
                },
                userList: {},
                roleList: {},
                departmentList: [
                    {
                        id: '1',
                        name: '1',
                        children: [
                            {
                                id: '1-1',
                                name: '1-1'
                            },
                            {
                                id: '1-2',
                                name: '1-2'
                            }
                        ]
                    }, {
                        id: '2',
                        name: '2',
                        children: [
                            {
                                id: '2-1',
                                name: '2-1'
                            },
                            {
                                id: '2-2',
                                name: '2-2'
                            }
                        ]
                    }
                ],
                actionList: [
                    { id: 'MANAGE', name: '管理' },
                    { id: 'READ', name: '查看' },
                    { id: 'WRITE', name: '新增' },
                    { id: 'DELETE', name: '删除' },
                    { id: 'UPDATE', name: '修改' }
                ]
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.name
            }
        },
        created () {
            this.getRepoUserList().then(res => {
                this.userList = res.reduce((target, item) => {
                    target[item.userId] = {
                        id: item.userId,
                        name: item.name
                    }
                    return target
                }, {})
            })
            this.getRepoRoleList({
                projectId: this.projectId,
                repoName: this.repoName
            }).then(res => {
                this.roleList = res.reduce((target, item) => {
                    target[item.id] = item
                    return target
                }, {})
            })
            this.getRepoDepartmentList()
            this.handlePermissionDetail()
        },
        methods: {
            ...mapActions([
                'getPermissionDetail',
                'getRepoUserList',
                'getRepoRoleList',
                'getRepoDepartmentList',
                'setUserPermission',
                'setRolePermission',
                'setDepartmentPermission',
                'setActionPermission'
            ]),
            getName (part, tag) {
                const map = {
                    users: this.userList,
                    roles: this.roleList
                }[part]
                return map[tag] ? map[tag].name : tag
            },
            getActions (actions) {
                return actions.map(v => this.actionList.find(w => w.id === v).name).join('，')
            },
            async handlePermissionDetail (target, origin, id) {
                this.isLoading = true
                await this.getPermissionDetail({
                    projectId: this.projectId,
                    repoName: this.repoName
                }).then(res => {
                    if (target && origin && id) {
                        this[origin][target].data = res.find(v => v.id === id)[target]
                    } else {
                        res.forEach(part => {
                            const perm = this[part.permName.replace(/^.*_([^_]+)$/, '$1')]
                            perm.id = part.id
                            perm.users.data = part.users
                            perm.roles.data = part.roles
                            perm.departments.data = part.departments
                            perm.actions.data = part.actions
                        })
                    }
                }).finally(() => {
                    this.isLoading = false
                })
            },
            submit (type, part, section) {
                this.loading = true
                const fn = {
                    users: this.setUserPermission,
                    roles: this.setRolePermission,
                    departments: this.setDepartmentPermission
                }[part]
                const key = {
                    users: 'userId',
                    roles: 'rId',
                    departments: 'departmentId'
                }[part]
                const value = {
                    add: [...section[part].data, ...section[part].addList],
                    delete: section[part].data.filter(v => !section[part].deleteList.find(w => w === v))
                }[type]
                fn({
                    body: {
                        permissionId: section.id,
                        [key]: value
                    }
                }).then(async res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (type === 'add' ? this.$t('add') : this.$t('save')) + this.$t('success')
                    })
                    await this.handlePermissionDetail(part, section.name, section.id)
                    section[part][`${type}List`] = []
                }).finally(() => {
                    this.loading = false
                })
            },
            editActionsDialogHandler (data) {
                this.editActionsDialog = {
                    show: true,
                    loading: false,
                    id: data.id,
                    name: data.name,
                    title: data.title + '权限配置',
                    actions: JSON.parse(JSON.stringify(data.actions.data))
                }
            },
            handleActionPermission () {
                this.loading = true
                this.setActionPermission({
                    body: {
                        permissionId: this.editActionsDialog.id,
                        actions: this.editActionsDialog.actions
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
                    })
                    this.editActionsDialog.show = false
                    this.handlePermissionDetail('actions', this.editActionsDialog.name, this.editActionsDialog.id)
                }).finally(() => {
                    this.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.permission-config-container {
    /deep/ .bk-collapse-item {
        margin-bottom: 20px;
        .bk-collapse-item-detail {
            color: inherit;
        }
        .bk-collapse-item-header {
            position: relative;
            height: 48px;
            line-height: 48px;
            .icon-angle-right {
                padding: 0 15px;
            }
        }
    }
    section + section {
        margin-top: 20px;
    }
    .section-header {
        padding-left: 20px;
        color: $fontBoldColor;
        background-color: #f2f2f2;
        border: 1px solid #d5d5d5;
        font-size: 16px;
        font-weight: normal;
        .icon-edit {
            font-size: 14px;
        }
        .permission-actions {
            font-size: 14px;
            color: $fontColor;
        }
    }
    .section-main {
        padding: 10px;
        border: 1px solid #d5d5d5;
        /deep/ .bk-select-empty {
            display: none;
        }
        .section-sub-title {
            padding: 10px;
            font-weight: bold;
            border-bottom: 1px solid #d5d5d5;
        }
        .section-sub-main {
            display: flex;
            flex-wrap: wrap;
            .permission-tag {
                position: relative;
                margin-right: 15px;
                margin-bottom: 10px;
                padding: 10px 20px;
                background-color: #f2f2f2;
                .icon-close-circle-shape {
                    display: none;
                    position: absolute;
                    top: -5px;
                    right: -5px;
                    color: $dangerColor;
                    cursor: pointer;
                }
                &:hover .icon-close-circle-shape {
                    display: block;
                }
            }
        }
    }
}
</style>
