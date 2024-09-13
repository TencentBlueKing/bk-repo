<template>
    <div>
        <vue-office-docx
            v-if="previewDocx"
            :src="dataSource"
            style="max-height: 100vh; overflow-y: auto"
        />
        <vue-office-excel
            v-if="previewExcel"
            :src="dataSource"
            :options="excelOptions"
            style="max-height: 100vh; overflow-y: auto"
        />
        <vue-office-pdf
            v-if="previewPdf"
            :src="dataSource"
        />
        <div v-if="previewBasic" class="flex-column flex-center">
            <div class="preview-file-tips">{{ $t('previewFileTips') }}</div>
            <textarea v-model="basicFileText" class="textarea" readonly></textarea>
        </div>
        <div v-if="hasError" class="empty-data-container flex-center" style="background-color: white; height: 100%">
            <div class="flex-column flex-center">
                <img width="480" height="240" style="float: left;margin-right: 3px" src="/ui/440.svg" />
                <span class="mt5 error-data-title">{{ $t('previewErrorTip') }}</span>
            </div>
        </div>
    </div>
</template>
<script>
    import VueOfficePdf from '@vue-office/pdf'
    import VueOfficeExcel from '@vue-office/excel'
    import VueOfficeDocx from '@vue-office/docx'
    import { customizePreviewOfficeFile } from '@/utils/previewOfficeFile'
    import { mapActions } from 'vuex'

    export default {
        name: 'filePreview',
        components: { VueOfficeDocx, VueOfficeExcel, VueOfficePdf },
        props: {
            repoName: String,
            filePath: String
        },
        data () {
            return {
                dataSource: '',
                previewDialog: {
                    title: '',
                    show: false,
                    isLoading: false
                },
                dialogWidth: window.innerWidth - 1000,
                excelOptions: {
                    xls: false, // 预览xlsx文件设为false；预览xls文件设为true
                    minColLength: 0, // excel最少渲染多少列，如果想实现xlsx文件内容有几列，就渲染几列，可以将此值设置为0.
                    minRowLength: 0, // excel最少渲染多少行，如果想实现根据xlsx实际函数渲染，可以将此值设置为0.
                    widthOffset: 10, // 如果渲染出来的结果感觉单元格宽度不够，可以在默认渲染的列表宽度上再加 Npx宽
                    heightOffset: 10, // 在默认渲染的列表高度上再加 Npx高
                    beforeTransformData: (workbookData) => {
                        return workbookData
                    }, // 底层通过exceljs获取excel文件内容，通过该钩子函数，可以对获取的excel文件内容进行修改，比如某个单元格的数据显示不正确，可以在此自行修改每个单元格的value值。
                    transformData: (workbookData) => {
                        return workbookData
                    } // 将获取到的excel数据进行处理之后且渲染到页面之前，可通过transformData对即将渲染的数据及样式进行修改，此时每个单元格的text值就是即将渲染到页面上的内容
                },
                previewDocx: false,
                previewExcel: false,
                previewPdf: false,
                previewBasic: false,
                basicFileText: '',
                hasError: false
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            }
        },
        async created () {
            if (this.filePath.endsWith('.pdf')
                || this.filePath.endsWith('.docx')
                || this.filePath.endsWith('.xls')
                || this.filePath.endsWith('.xlsx')
            ) {
                customizePreviewOfficeFile(this.projectId, this.repoName, '/' + this.filePath).then(res => {
                    if (this.filePath.endsWith('docx')) {
                        this.previewDocx = true
                    } else if (this.filePath.endsWith('xlsx')) {
                        this.previewExcel = true
                    } else if (this.filePath.endsWith('xls')) {
                        this.excelOptions.xls = true
                        this.previewExcel = true
                    } else {
                        this.previewPdf = true
                    }
                    this.dataSource = res.data
                }).catch((e) => {
                    this.hasError = true
                })
            } else if (this.filePath.endsWith('txt')
                || this.filePath.endsWith('sh')
                || this.filePath.endsWith('bat')
                || this.filePath.endsWith('json')
                || this.filePath.endsWith('yaml')
                || this.filePath.endsWith('xml')
                || this.filePath.endsWith('log')
                || this.filePath.endsWith('ini')
                || this.filePath.endsWith('log')
                || this.filePath.endsWith('properties')
                || this.filePath.endsWith('toml')) {
                this.previewBasicFile({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    path: '/' + this.filePath
                }).then(res => {
                    this.previewBasic = true
                    this.basicFileText = res
                }).catch((e) => {
                    this.hasError = true
                })
            } else {
                this.hasError = true
            }
        },
        destroyed () {
            this.cancel()
        },
        methods: {
            ...mapActions([
                'previewBasicFile'
            ]),
            cancel () {
                this.dataSource = ''
                this.previewDocx = false
                this.previewExcel = false
                this.previewPdf = false
                this.previewBasic = false
                this.hasError = false
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@vue-office/docx/lib/index.css';
@import '@vue-office/excel/lib/index.css';
.preview-file-tips {
    margin-bottom: 10px;
    color: #707070;
}
.textarea {
    resize: none;
    width: 100%;
    height: 700px;
    max-height: 700px;
    overflow-y: auto;
    border: 1px solid #ccc;
    padding: 0 5px;
}
.error-data-title {
    margin-top: 18px;
    font-size: 24px;
    line-height: 32px;
}
</style>
