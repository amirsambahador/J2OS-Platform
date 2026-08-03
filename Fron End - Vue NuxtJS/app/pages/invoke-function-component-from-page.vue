<script setup>
const myComponent = ref()
onMounted(() => {
  myComponent.value.componentMethod()
})
</script>

<template>
  <div>
    اینجا پیج است و پیج متد کامپونت را صدا کرده است
    <InvokeFunctionComponentFromPage ref="myComponent" />
  </div>
</template>
