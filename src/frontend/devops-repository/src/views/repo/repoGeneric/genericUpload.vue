<template>
    <bk-dialog
        v-model="show"
        :title="title"
        :quick-close="false"
        :mask-close="false"
        :close-icon="false"
        width="620"
        header-position="left">
        <artifactory-upload
            ref="artifactoryUpload"
            @upload-failed="uploadFailed">
        </artifactory-upload>
        <div slot="footer">
            <bk-button :loading="loading" theme="primary" @click="submitUpload">{{ $t('upload') }}</bk-button>
            <bk-button @click="loading ? abortUpload() : cancelUpload()">{{ $t(loading ? 'abort' : 'cancel') }}</bk-button>
        </div>
    </bk-dialog>
</template>
<script>
    import ArtifactoryUpload from '@/components/ArtifactoryUpload'
    import { mapActions } from 'vuex'
    export default {
        name: 'genericUpload',
        components: { ArtifactoryUpload },
        props: {
            show: Boolean,
            title: String,
            fullPath: String
        },
        data () {
            return {
                loading: false,
                uploadXHR: null
            }
        },
        watch: {
            show (val) {
                if (val) this.$refs.artifactoryUpload.reset()
            }
        },
        methods: {
            ...mapActions([
                'uploadArtifactory'
            ]),
            uploadFile (file, progressHandler) {
                this.loading = true
                this.uploadXHR && this.uploadXHR.abort()
                this.uploadXHR = new XMLHttpRequest()
                this.uploadArtifactory({
                    xhr: this.uploadXHR,
                    projectId: this.$route.params.projectId,
                    repoName: this.$route.query.name,
                    fullPath: `${this.fullPath}/${file.name}`,
                    body: file.blob,
                    progressHandler,
                    headers: {
                        'Content-Type': file.type || 'application/octet-stream',
                        'X-BKREPO-OVERWRITE': file.overwrite,
                        'X-BKREPO-EXPIRES': file.expires
                    }
                }).then(() => {
                    this.cancelUpload()
                    this.$bkMessage({
                        theme: 'success',
                        message: `${this.$t('upload')} ${file.name} ${this.$t('success')}`
                    })
                }).catch(e => {
                    this.$refs.artifactoryUpload.uploadFailed()
                    e && this.$bkMessage({
                        theme: 'error',
                        message: e.message || e
                    })
                }).finally(() => {
                    this.loading = false
                })
            },
            async submitUpload () {
                const { file, progressHandler } = await this.$refs.artifactoryUpload.getFiles()
                if (!file.overwrite) {
                    this.loading = true
                    const url = `/generic/${this.$route.params.projectId}/${this.$route.query.name}/${encodeURIComponent(`${this.fullPath}/${file.name}`)}`
                    this.$ajax.head(url).then(() => {
                        this.$bkMessage({
                            theme: 'error',
                            message: this.$t('fileExist')
                        })
                        this.loading = false
                    }).catch(e => {
                        if (e.status === 404) {
                            this.uploadFile(file, progressHandler)
                        } else if (e.status === 403) {
                            this.$bkMessage({
                                theme: 'error',
                                message: e.message
                            })
                            this.loading = false
                        }
                    })
                } else {
                    this.uploadFile(file, progressHandler)
                }
            },
            abortUpload () {
                this.loading = false
                this.uploadXHR && this.uploadXHR.abort()
                this.uploadXHR = null
            },
            cancelUpload () {
                this.$emit('cancel')
                this.abortUpload()
            },
            uploadFailed (file) {
                this.abortUpload()
                this.$bkMessage({
                    theme: 'error',
                    message: `${this.$t('upload')} ${file.name} ${this.$t('fail')}`
                })
            }
        }
    }
</script>
