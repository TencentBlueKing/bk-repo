<template>
    <bk-breadcrumb separator-class="bk-icon icon-angle-right">
        <slot></slot>
        <bk-breadcrumb-item
            v-for="item in list"
            :key="item.name"
            :to="{ name: item.to || item.name, params: { ...$route.query, ...$route.params }, query: $route.query }">
            {{ transformLabel(item.label,item.name) || $t(item.template) }}
        </bk-breadcrumb-item>
    </bk-breadcrumb>
</template>
<script>
    export default {
        name: 'topBreadCrumb',
        computed: {
            list () {
                return this.$route.matched.map(r => r.meta.breadcrumb || []).flat(Infinity)
            }
        },
        methods: {
            transformLabel (label, name) {
                const ctx = { ...this.$route.params, ...this.$route.query }
                const transformLabel = label.replace(/\{(.*?)\}/g, (_, $1) => {
                    return $1 in ctx ? ctx[$1] : ''
                })
                return this.replaceRepoName(transformLabel === label ? this.$t(name) : transformLabel)
            }
        }
    }
</script>
