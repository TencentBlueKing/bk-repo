<template>
    <div class="move-split-bar flex-center"
        draggable="false"
        :style="{
            [left ? 'width' : 'height']: !Number(width) ? width : `${width}px`,
            [left ? 'height' : 'width']: '100%'
        }">
        <div
            :style="{
                [left ? 'width' : 'height']: '40%',
                [left ? 'height' : 'width']: '5%'
            }">
        </div>
    </div>
</template>
<script>
    export default {
        name: 'moveSplitBar',
        props: {
            left: Number,
            top: Number,
            width: {
                type: [Number, String],
                default: 10
            }
        },
        data () {
            return {
                startDrag: false,
                startPosition: 0,
                initOffset: 0
            }
        },
        mounted () {
            this.$el.addEventListener('mousedown', this.dragDown)
            window.addEventListener('mousemove', this.dragMove)
            window.addEventListener('mouseup', this.dragUp)
        },
        beforeDestroy () {
            window.removeEventListener('mousemove', this.dragMove)
            window.removeEventListener('mouseup', this.dragUp)
        },
        methods: {
            dragDown (e) {
                this.startDrag = true
                // 确定起始位置
                this.startPosition = this.left ? e.clientX : e.clientY
                this.initOffset = this.left || this.top
            },
            dragMove (e) {
                if (!this.startDrag) return
                const offset = (this.left ? e.clientX : e.clientY) - this.startPosition
                this.$emit('change', this.initOffset + offset)
            },
            dragUp () {
                this.startDrag = false
            }
        }
    }
</script>
<style lang="scss" scoped>
.move-split-bar {
    cursor: col-resize;
    &:hover div {
        background-color: var(--primaryColor);
    }
}
</style>
