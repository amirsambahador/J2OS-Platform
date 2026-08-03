<template>
  <div>
    <UInput v-model="firstName" />
  </div>
</template>

<script setup>
const firstName = ref('Ali')

watch(firstName, (newValue, oldValue) => {
  alert('Before ' + oldValue)
  alert('After ' + newValue)
})
</script>
