<template>
    <canway-dialog
        :value="show"
        width="528"
        height-num="274"
        title="添加文件路径"
        @cancel="$emit('cancel')"
        @confirm="confirmPackageData">
        <bk-input
            class="w480"
            v-model="pathsStr"
            type="textarea"
            placeholder="请输入文件路径，以换行分隔"
            :rows="6">
        </bk-input>
    </canway-dialog>
</template>
<script>
    export default {
        name: 'pathDialog',
        props: {
            show: Boolean,
            pathConstraints: Array
        },
        data () {
            return {
                pathsStr: ''
            }
        },
        watch: {
            show (val) {
                if (!val) return
                this.pathsStr = this.pathConstraints.join('\n')
            }
        },
        methods: {
            async confirmPackageData () {
                this.$emit('confirm', this.pathsStr.split(/\n/).filter(Boolean))
                this.$emit('cancel')
            }
        }
    }
</script>
