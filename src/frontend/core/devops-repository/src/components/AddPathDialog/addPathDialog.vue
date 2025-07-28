<template>
    <canway-dialog
        v-model="showDialog"
        theme="primary"
        width="800"
        class="update-user-dialog"
        height-num="603"
        :title="$t('add') + $t('space') + $t('path')"
        @cancel="cancel">
        <div style="display: flex">
            <div class="ml10 mr10 mt10" style="width: 50%; text-align: center">
                <div>
                    <div style="align-items: center">
                        <bk-input :type="'textarea'" :placeholder="$t('userPathPlaceholder')" v-model="editPathConfig.newPath" class="w350 usersTextarea" />
                    </div>
                    <div class="mt5" style="display: flex">
                        <div class="mr10" style="text-align: left">
                            <bk-button style="width: 240px" theme="primary" @click="parseFn">{{ $t('add') }}</bk-button>
                        </div>
                        <div style="text-align: left">
                            <bk-button style="width: 110px" @click="editPathConfig.newPath = ''">{{ $t('clear') }}</bk-button>
                        </div>
                    </div>
                </div>
            </div>
            <div class="ml10 mr10 mt10" style="width: 50%;text-align: center">
                <div style="display: flex">
                    <bk-input v-model="editPathConfig.search" :placeholder="$t('search')" style="width: 280px" @change="filterPaths" />
                    <bk-button class="ml10" theme="primary" @click="copy">{{ $t('copyAll') }}</bk-button>
                </div>
                <div v-show="editPathConfig.paths.length" class="mt10 update-user-list">
                    <div class="pl10 pr10 update-user-item flex-between-center" v-for="(path, index) in editPathConfig.paths" :key="index">
                        <div class="flex-align-center">
                            <span class="update-user-name text-overflow" :title="path">{{ path }}</span>
                        </div>
                        <Icon class="ml10 hover-btn" size="24" name="icon-delete" @click.native="deletePath(index)" />
                    </div>
                </div>
            </div>
        </div>
        <template #footer>
            <bk-button @click="cancel">{{ $t('cancel') }}</bk-button>
            <bk-button class="ml10" theme="primary" @click="confirm">{{ $t('confirm') }}</bk-button>
        </template>
    </canway-dialog>
</template>

<script>
    import { copyToClipboard } from '@/utils'
    import { mapActions } from 'vuex'
    import _ from 'lodash'

    export default {
        name: 'AddPathDialog',
        props: {
            visible: Boolean,
            showData: {
                type: Array,
                default: () => []
            }
        },
        data () {
            return {
                showDialog: this.visible,
                editPathConfig: {
                    paths: [],
                    search: '',
                    newPath: '',
                    originPaths: []
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            }
        },
        watch: {
            visible: function (newVal) {
                if (newVal) {
                    this.showDialog = true
                    this.editPathConfig.paths = _.cloneDeep(this.showData)
                    this.editPathConfig.originPaths = _.cloneDeep(this.showData)
                } else {
                    this.cancel()
                }
            }
        },
        methods: {
            ...mapActions([
                'getCorrectFolder'
            ]),
            filterPaths () {
                this.editPathConfig.paths = this.editPathConfig.originPaths
                if (this.editPathConfig.search !== '') {
                    this.editPathConfig.paths = this.editPathConfig.paths.filter(user => user.toLowerCase().includes(this.editPathConfig.search.toLowerCase()))
                }
            },
            parseFn () {
                const data = this.editPathConfig.newPath
                if (data !== '') {
                    const temp = []
                    this.editPathConfig.search = ''
                    const paths = data.toString().replace(/\n/g, ',').replace(/\s/g, ',').split(',')
                    for (let i = 0; i < paths.length; i++) {
                        paths[i] = paths[i].toString().trim()
                        const tempPath = paths[i].replace(/\/+$/, '')
                        if (tempPath !== '') {
                            temp.push(tempPath)
                        }
                    }
                    for (let i = 0; i < this.editPathConfig.originPaths.length; i++) {
                        temp.push(this.editPathConfig.originPaths[i])
                    }
                    const fullPaths = Array.from(new Set(temp))
                    const param = {
                        projectId: this.projectId,
                        repoName: this.repoName,
                        fullPaths: fullPaths
                    }
                    this.getCorrectFolder({ path: param }).then(res => {
                        this.editPathConfig.paths = res
                        this.editPathConfig.originPaths = res
                        this.editPathConfig.newPath = ''
                        const noExistFolders = fullPaths.filter(path => !res.includes(path)).join(',')
                        if (noExistFolders) {
                            this.$bkMessage({
                                theme: 'error',
                                message: this.$t('folderNoExistTip', { 0: noExistFolders })
                            })
                        }
                    }).catch(err => {
                        this.$bkMessage({
                            theme: 'error',
                            message: err
                        })
                    })
                }
            },
            deletePath (index) {
                const temp = []
                for (let i = 0; i < this.editPathConfig.paths.length; i++) {
                    if (i !== index) {
                        temp.push(this.editPathConfig.paths[i])
                    }
                }
                this.editPathConfig.paths = temp
                this.editPathConfig.originPaths = temp
            },
            copy () {
                const text = this.editPathConfig.originPaths.join('\n')
                copyToClipboard(text).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('copy') + this.$t('space') + this.$t('success')
                    })
                }).catch(() => {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('copy') + this.$t('space') + this.$t('fail')
                    })
                })
            },
            cancel () {
                this.showDialog = false
                this.$emit('update:visible', false)
            },
            confirm () {
                this.showDialog = false
                this.$emit('update:visible', false)
                this.$emit('complete', this.editPathConfig.originPaths)
            }
        }
    }
</script>

<style lang="scss" scoped>
.update-user-dialog {
    ::v-deep .usersTextarea .bk-textarea-wrapper .bk-form-textarea{
        height: 500px;
    }
    .update-user-list {
        display: grid;
        grid-template: auto / repeat(1, 1fr);
        gap: 10px;
        max-height: 500px;
        overflow-y: auto;
        .update-user-item {
            height: 32px;
            border: 1px solid var(--borderWeightColor);
            background-color: var(--bgLighterColor);
            .update-user-name {
                max-width: 300px;
                margin-left: 5px;
            }
        }
    }
}

</style>
