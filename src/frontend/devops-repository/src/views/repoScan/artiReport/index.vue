<template>
    <component :is="componentName"></component>
</template>
<script>
    import leakComponent from './leak'
    import licenseComponent from './license'
    export default {
        components: { leakComponent, licenseComponent },
        computed: {
            scanType () {
                return this.$route.query.scanType || ''
            },
            componentName () {
                return this.scanType.includes('LICENSE') ? 'license-component' : 'leak-component'
            }
        }
    }
</script>
