<template>
  <div>
    <UInput v-model="firstName" />
    <br>
    <UInput v-model="lastName" />
    <br>
    {{ fullName }}
  </div>
</template>

<script setup>
const firstName = ref('Ali')
const lastName = ref('Ahmadi')

const fullName = computed(() => {
  return `${firstName.value} ${lastName.value}`
})
</script>
