<template>
    <bk-dialog v-model="showItsmDialog" :visible.sync="showItsmDialog" :before-close="close" style="text-align: center">
        <img src="/ui/no-permission.svg" />
        <p>{{ $t('itsmTip') }}</p>
        <div slot="footer">
            <bk-button @click="open" theme="primary" class="mr10">{{ $t('viewApproval') }}</bk-button>
            <bk-button type="primary" @click="close" class="mr10">{{ $t('cancel') }}</bk-button>
        </div>
    </bk-dialog>
</template>

<script>
    export default {
        name: 'ItsmDialog',
        props: {
            visible: Boolean,
            showData: {
                type: Object,
                default: undefined
            }
        },
        data () {
            return {
                showItsmDialog: this.visible,
                formData: []
            }
        },
        watch: {
            visible: function (newVal) {
                if (newVal) {
                    this.showItsmDialog = true
                    this.formData = []
                    this.formData.push(this.showData)
                } else {
                    this.close()
                }
            }
        },
        methods: {
            close () {
                this.showItsmDialog = false
                this.$emit('update:visible', false)
            },
            open () {
                window.open(this.showData.url, '_blank')
            }
        }
    }
</script>

<style lang="scss" scoped>
.bk-dialog {
  position: fixed;
  top: 40%;
  left: 40%;
  transform: translate(-50%, -50%);
  z-index: 1000; /* 确保它在其他元素之上 */
  background-color: white; /* 背景颜色 */
  padding: 20px; /* 内边距 */
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1); /* 阴影效果 */
  border-radius: 8px; /* 圆角 */
}

</style>
