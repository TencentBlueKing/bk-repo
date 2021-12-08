<template>
    <canway-dialog
        :value="show"
        :title="title"
        width="620"
        height-num="280"
        @cancel="$emit('cancel')">
        <artifactory-upload ref="artifactoryUpload" :upload-status="uploadStatus" :upload-progress="uploadProgress"></artifactory-upload>
        <div slot="footer">
            <bk-button @click="loading ? abortUpload() : $emit('cancel')">{{ $t('cancel') }}</bk-button>
            <bk-button class="ml10" :loading="loading" theme="primary" @click="submitUpload">{{ $t('confirm') }}</bk-button>
        </div>
    </canway-dialog>
</template>
<script>
    import ArtifactoryUpload from '@repository/components/ArtifactoryUpload'
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
                uploadXHR: null,
                uploadStatus: 'primary',
                uploadProgress: 0
            }
        },
        watch: {
            show (val) {
                if (val) {
                    this.$refs.artifactoryUpload.reset()
                    this.loading = false
                    this.uploadStatus = 'primary'
                    this.uploadProgress = 0
                }
            }
        },
        methods: {
            ...mapActions([
                'uploadArtifactory'
            ]),
            uploadFile (file) {
                this.loading = true
                this.uploadXHR = new XMLHttpRequest()
                this.uploadStatus = 'primary'
                this.uploadProgress = 0
                this.uploadArtifactory({
                    xhr: this.uploadXHR,
                    projectId: this.$route.params.projectId,
                    repoName: this.$route.query.repoName,
                    fullPath: `${this.fullPath}/${file.name}`,
                    body: file.blob,
                    progressHandler: this.progressHandler,
                    headers: {
                        'Content-Type': file.type || 'application/octet-stream',
                        'X-BKREPO-OVERWRITE': file.overwrite,
                        'X-BKREPO-EXPIRES': file.expires
                    }
                }).then(() => {
                    this.$emit('update')
                    this.$emit('cancel')
                    this.$bkMessage({
                        theme: 'success',
                        message: `${this.$t('upload')} ${file.name} ${this.$t('success')}`
                    })
                }).catch(e => {
                    this.uploadStatus = 'primary'
                    this.uploadProgress = 0
                    e && this.$bkMessage({
                        theme: 'error',
                        message: e.message || e
                    })
                }).finally(() => {
                    this.loading = false
                })
            },
            async submitUpload () {
                const file = await this.$refs.artifactoryUpload.getFiles()
                if (!file.overwrite) {
                    this.loading = true
                    const url = `/generic/${this.$route.params.projectId}/${this.$route.query.repoName}/${encodeURIComponent(`${this.fullPath}/${file.name}`)}`
                    this.$ajax.head(url).then(() => {
                        this.$bkMessage({
                            theme: 'error',
                            message: this.$t('fileExist')
                        })
                        this.loading = false
                    }).catch(e => {
                        if (e.status === 404) {
                            this.uploadFile(file)
                        } else if (e.status === 403) {
                            this.$bkMessage({
                                theme: 'error',
                                message: e.message
                            })
                            this.loading = false
                        }
                    })
                } else {
                    this.uploadFile(file)
                }
            },
            abortUpload () {
                this.loading = false
                this.uploadXHR && this.uploadXHR.abort()
                this.uploadXHR = null
            },
            progressHandler ($event) {
                console.log('upload', $event.loaded + '/' + $event.total)
                this.uploadProgress = $event.loaded / $event.total
            }
        }
    }
</script>
