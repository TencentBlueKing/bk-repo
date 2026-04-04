<script>
export default {
  name: 'MenuItem',
  functional: true,
  props: {
    icon: {
      type: String,
      default: ''
    },
    title: {
      type: String,
      default: ''
    }
  },
  render(h, context) {
    const { icon, title } = context.props
    const vnodes = []

    if (icon) {
      if (icon.includes('el-icon')) {
        vnodes.push(h('i', { class: [icon, 'sub-el-icon'] }))
      } else {
        vnodes.push(h('svg-icon', { attrs: { 'icon-class': icon } }))
      }
    }

    if (title) {
      vnodes.push(h('span', { slot: 'title' }, [title]))
    }
    return vnodes
  }
}
</script>

<style scoped>
.sub-el-icon {
  color: currentColor;
  width: 1em;
  height: 1em;
}
</style>
