<script setup>
const loader = useFileLoader()
onMounted(() => {
  loader.importStyleSheetFile('/external-css/external-css.css')
  loader.importJavaScriptSourceFile('/external-js/external-js.js')
})
</script>

<template>
  <input
    type="button"
    class="external-class"
    onclick="externalFunction()"
    value="OK"
  >
</template>
