<template>
    <canway-dialog
        :value="uploadDialog.show"
        :title="uploadDialog.title"
        width="620"
        :height-num="heightNum"
        @cancel="uploadDialog.show = false">
        <slot></slot>
        <artifactory-upload ref="artifactoryUpload"
            :upload-status="uploadDialog.uploadStatus"
            :upload-progress="uploadDialog.uploadProgress">
        </artifactory-upload>
        <template #footer>
            <bk-button @click="uploadDialog.loading ? abortUpload() : (uploadDialog.show = false)">{{ $t('cancel') }}</bk-button>
            <bk-button class="ml10" :loading="uploadDialog.loading" theme="primary" @click="submitUpload">{{ $t('confirm') }}</bk-button>
        </template>
        <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </canway-dialog>
</template>
<script>
    import ArtifactoryUpload from '@repository/components/ArtifactoryUpload'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    import { mapActions, mapState } from 'vuex'
    export default {
        name: 'genericUpload',
        components: { ArtifactoryUpload, iamDenyDialog },
        props: {
            heightNum: {
                type: String,
                default: '267'
            }
        },
        data () {
            return {
                uploadDialog: {
                    projectId: '',
                    repoName: '',
                    show: false,
                    title: '',
                    fullPath: '',
                    loading: false,
                    uploadXHR: null,
                    uploadStatus: 'primary',
                    uploadProgress: 0
                },
                showIamDenyDialog: false,
                showData: {}
            }
        },
        computed: {
            ...mapState(['userInfo']),
            projectId () {
                return this.uploadDialog.projectId || this.$route.params.projectId
            },
            repoName () {
                return this.uploadDialog.repoName || this.$route.query.repoName
            }
        },
        methods: {
            ...mapActions([
                'uploadArtifactory',
                'getPermissionUrl'
            ]),
            setData (data, refresh = false) {
                if (refresh) {
                    this.uploadDialog = {
                        ...this.uploadDialog,
                        ...data
                    }
                    return
                }
                this.uploadDialog = {
                    ...this.uploadDialog,
                    loading: false,
                    uploadXHR: null,
                    uploadStatus: 'primary',
                    uploadProgress: 0,
                    ...data
                }
                this.$refs.artifactoryUpload && this.$refs.artifactoryUpload.reset()
            },
            uploadFile (file) {
                this.uploadDialog = {
                    ...this.uploadDialog,
                    loading: true,
                    uploadXHR: new XMLHttpRequest(),
                    uploadStatus: 'primary',
                    uploadProgress: 0
                }
                this.uploadArtifactory({
                    xhr: this.uploadDialog.uploadXHR,
                    projectId: this.projectId,
                    repoName: this.repoName,
                    fullPath: `${this.uploadDialog.fullPath}/${file.name}`,
                    body: file.blob,
                    progressHandler: this.progressHandler,
                    headers: {
                        'Content-Type': file.type || 'application/octet-stream',
                        'X-BKREPO-OVERWRITE': file.overwrite,
                        'X-BKREPO-EXPIRES': file.expires
                    }
                }).then(() => {
                    this.$emit('update')
                    this.uploadDialog.show = false
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('upload') + this.$t('space') + `${file.name}` + this.$t('space') + this.$t('success')
                    })
                }).catch(e => {
                    this.uploadDialog.uploadStatus = 'primary'
                    this.uploadDialog.uploadProgress = 0
                    if (e.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: this.projectId,
                                action: 'WRITE',
                                resourceType: 'REPO',
                                uid: this.userInfo.name,
                                repoName: this.repoName
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: this.projectId,
                                    repoName: this.repoName,
                                    action: 'WRITE',
                                    url: res
                                }
                            } else {
                                this.$bkMessage({
                                    theme: 'error',
                                    message: e.message
                                })
                            }
                        })
                    } else {
                        e && this.$bkMessage({
                            theme: 'error',
                            message: e.message
                        })
                    }
                }).finally(() => {
                    this.uploadDialog.loading = false
                })
            },
            async submitUpload () {
                const file = await this.$refs.artifactoryUpload.getFiles()
                if (!file.overwrite) {
                    this.uploadDialog.loading = true
                    const url = `/generic/${this.projectId}/${this.repoName}/${encodeURIComponent(`${this.uploadDialog.fullPath}/${file.name}`)}`
                    this.$ajax.head(url).then(() => {
                        this.$bkMessage({
                            theme: 'error',
                            message: this.$t('fileExist')
                        })
                        this.uploadDialog.loading = false
                    }).catch(e => {
                        if (e.status === 404) {
                            this.uploadFile(file)
                        } else if (e.status === 403) {
                            this.getPermissionUrl({
                                body: {
                                    projectId: this.projectId,
                                    action: 'WRITE',
                                    resourceType: 'REPO',
                                    uid: this.userInfo.name,
                                    repoName: this.repoName
                                }
                            }).then(res => {
                                if (res !== '') {
                                    this.showIamDenyDialog = true
                                    this.showData = {
                                        projectId: this.projectId,
                                        repoName: this.repoName,
                                        action: 'WRITE',
                                        url: res
                                    }
                                } else {
                                    this.$bkMessage({
                                        theme: 'error',
                                        message: e.message
                                    })
                                    this.uploadDialog.loading = false
                                }
                            })
                        }
                    })
                } else {
                    this.uploadFile(file)
                }
            },
            abortUpload () {
                this.uploadDialog.loading = false
                this.uploadDialog.uploadXHR && this.uploadDialog.uploadXHR.abort()
                this.uploadDialog.uploadXHR = null
            },
            progressHandler ($event) {
                console.log('upload', $event.loaded + '/' + $event.total)
                this.uploadDialog.uploadProgress = $event.loaded / $event.total
            }
        }
    }
</script>
