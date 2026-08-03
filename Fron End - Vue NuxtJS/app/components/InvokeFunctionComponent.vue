<script setup>
const emit = defineEmits(['page-method1', 'page-method2'])

onMounted(() => {
  emit('pageMethod1')
  emit('pageMethod2')
})
</script>

<template>
  <div>
    اینجا کامپوننت است و کامپوننت متد صفحه را صدا کرده است
  </div>
</template>
