<template>
    <bk-collapse class="permission-config-container" v-model="activeName" v-bkloading="{ isLoading }">
        <bk-collapse-item v-for="section in [admin, user]" :key="section.name" :name="section.name">
            <header class="section-header">
                <div class="flex-align-center">
                    <icon class="mr10" size="20" :name="section.icon"></icon>
                    <span>{{ section.title }}</span>
                    <span v-if="getActions(section.actions.data)" class="mr10 permission-actions">（{{ getActions(section.actions.data) }}）</span>
                    <i v-if="section === user" class="devops-icon icon-edit hover-btn" @click.stop="editActionsDialogHandler(section)"></i>
                </div>
            </header>
            <div slot="content" class="section-main">
                <template v-for="part in ['users', 'roles']">
                    <header :key="part + 'header'" class="section-sub-title flex-align-center">
                        <span>{{section[part].title}}</span>
                        <i class="ml10 devops-icon hover-btn"
                            :class="section[part].showAddArea ? 'icon-minus-square' : 'icon-plus-square'"
                            @click="handleShowAddArea(section[part])">
                        </i>
                        <div v-show="section[part].showAddArea" :key="part + 'operation'" class="ml15 flex-align-center">
                            <bk-tag-input
                                style="min-width: 250px"
                                v-model="section[part].addList"
                                :list="filterSelectOptions(section[part], part)"
                                :search-key="['id', 'name']"
                                placeholder="请输入，按Enter键确认"
                                trigger="focus"
                                allow-create>
                            </bk-tag-input>
                            <i v-if="section[part].addList.length"
                                class="section-sub-add-btn devops-icon icon-check-1"
                                @click="() => {
                                    submit('add', part, section)
                                }">
                            </i>
                        </div>
                    </header>
                    <div :key="part + 'data'" class="section-sub">
                        <div class="section-sub-main mt10">
                            <div class="permission-tag" v-for="tag in filterDeleteTagList(section[part])" :key="tag">
                                {{ getName(part, tag) }}
                                <i class="devops-icon icon-close-circle-shape" @click="handleDeleteTag(tag, part, section)"></i>
                            </div>
                        </div>
                    </div>
                </template>
            </div>
        </bk-collapse-item>
        <canway-dialog
            :value="editActionsDialog.show"
            width="410"
            height-num="274"
            :title="editActionsDialog.title"
            @cancel="editActionsDialog.show = false">
            <bk-checkbox-group v-model="editActionsDialog.actions">
                <bk-checkbox v-for="action in actionList" :key="action.id" class="m10" :value="action.id">{{ action.name }}</bk-checkbox>
            </bk-checkbox-group>
            <template #footer>
                <bk-button theme="default" @click.stop="editActionsDialog.show = false">{{$t('cancel')}}</bk-button>
                <bk-button :loading="editActionsDialog.loading" theme="primary" @click.stop.prevent="handleActionPermission">{{$t('submit')}}</bk-button>
            </template>
        </canway-dialog>
    </bk-collapse>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'permissionConfig',
        data () {
            return {
                isLoading: false,
                activeName: ['admin', 'user'],
                editActionsDialog: {
                    show: false,
                    loading: false,
                    id: '',
                    name: '',
                    title: '',
                    actions: []
                },
                admin: {
                    name: 'admin',
                    loading: false,
                    title: this.$t('admin'),
                    icon: 'perm-controller',
                    id: '',
                    actions: {
                        data: []
                    },
                    users: {
                        title: this.$t('user'),
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    roles: {
                        title: this.$t('userGroup'),
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    }
                },
                user: {
                    name: 'user',
                    loading: false,
                    title: this.$t('users'),
                    icon: 'perm-user',
                    id: '',
                    actions: {
                        data: []
                    },
                    users: {
                        title: this.$t('user'),
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    roles: {
                        title: this.$t('userGroup'),
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    }
                },
                userList: {},
                roleList: {},
                actionList: [
                    { id: 'WRITE', name: '上传' },
                    { id: 'UPDATE', name: '修改' },
                    { id: 'DELETE', name: '删除' }
                ]
            }
        },
        computed: {
            ...mapState(['userInfo']),
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            },
            getName () {
                return (part, tag) => {
                    const map = {
                        users: this.userList,
                        roles: this.roleList
                    }[part]
                    return map[tag] ? map[tag].name : tag
                }
            },
            getActions () {
                return (actions) => {
                    return actions.map(v => this.actionList.find(w => w.id === v)?.name).filter(Boolean).join('，')
                }
            },
            filterDeleteTagList () {
                return (target) => {
                    return target.data.filter(v => !target.deleteList.find(w => w === v))
                }
            }
        },
        created () {
            this.getProjectRoleList({
                projectId: this.projectId
            }).then(res => {
                this.roleList = res.reduce((target, item) => {
                    target[item.id] = item
                    return target
                }, {})
            })
            this.getProjectUserList({
                projectId: this.projectId
            }).then(res => {
                this.userList = res.reduce((target, item) => {
                    target[item.userId] = {
                        id: item.userId,
                        name: item.name
                    }
                    return target
                }, {})
            })
            this.handlePermissionDetail()
        },
        methods: {
            ...mapActions([
                'getPermissionDetail',
                'getProjectUserList',
                'getProjectRoleList',
                'setUserPermission',
                'setRolePermission',
                'setActionPermission'
            ]),
            filterSelectOptions (target, part) {
                const list = Object.values({ users: this.userList, roles: this.roleList }[part])
                return list
                    .filter(v => v.id !== 'anonymous')
                    .filter(v => !~target.data.findIndex(w => w === v.id))
            },
            handleShowAddArea (target) {
                target.showAddArea = !target.showAddArea
            },
            handleDeleteTag (tag, part, section) {
                section[part].deleteList.push(tag)
                this.submit('delete', part, section)
            },
            handlePermissionDetail (target, origin, id) {
                this.isLoading = true
                return this.getPermissionDetail({
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
                            perm.actions.data = part.actions
                        })
                    }
                }).finally(() => {
                    this.isLoading = false
                })
            },
            submit (type, part, section) {
                if (section.loading) return
                section.loading = true
                const fn = {
                    users: this.setUserPermission,
                    roles: this.setRolePermission
                }[part]
                const key = {
                    users: 'userId',
                    roles: 'rId'
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
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (type === 'add' ? this.$t('add') : this.$t('delete')) + this.$t('success')
                    })
                    this.handlePermissionDetail(part, section.name, section.id).then(() => {
                        section[part][`${type}List`] = []
                    })
                }).finally(() => {
                    section.loading = false
                })
            },
            cancel (target) {
                target.deleteList = []
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
                if (this.editActionsDialog.loading) return
                this.editActionsDialog.loading = true
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
                    this.editActionsDialog.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.permission-config-container {
    margin: 0 -10px;
    ::v-deep .bk-collapse-item {
        margin-bottom: 20px;
        .bk-collapse-item-detail {
            color: inherit;
        }
        .bk-collapse-item-header {
            position: relative;
            height: 42px;
            line-height: 40px;
            .icon-angle-right {
                padding: 0 15px;
            }
        }
    }
    .section-header {
        padding-left: 10px;
        color: var(--fontPrimaryColor);
        background-color: var(--bgHoverColor);
        border: 1px solid var(--borderColor);
        font-weight: bold;
        .permission-actions {
            font-size: 12px;
            font-weight: normal;
            color: var(--fontPrimaryColor);
        }
    }
    .section-main {
        padding: 10px;
        border: solid var(--borderColor);
        border-width: 0 1px 1px;
        ::v-deep .bk-select-empty {
            display: none;
        }
        .section-sub-title {
            height: 52px;
            padding: 10px;
            border-bottom: 1px solid var(--borderColor);
            > :first-child {
                flex-basis: 45px;
            }
            .section-sub-add-btn {
                position: relative;
                z-index: 1;
                padding: 9px;
                color: white;
                margin-left: -2px;
                border-radius: 0 2px 2px 0;
                background-color: var(--primaryColor);
                cursor: pointer;
                &:hover {
                    background-color: var(--primaryHoverColor);
                }
            }
        }
        .section-sub {
            margin-bottom: 20px;
            .section-sub-main {
                display: flex;
                flex-wrap: wrap;
                .permission-tag {
                    position: relative;
                    margin-right: 15px;
                    margin-bottom: 10px;
                    padding: 7px 20px;
                    background-color: var(--bgHoverColor);
                    .icon-close-circle-shape {
                        display: none;
                        position: absolute;
                        top: -5px;
                        right: -5px;
                        color: var(--dangerColor);
                        cursor: pointer;
                    }
                    &:hover .icon-close-circle-shape {
                        display: block;
                    }
                }
            }
        }
    }
}
</style>
