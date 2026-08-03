<script setup>
const error = useError()
</script>

<template>
  <div>
    <h1>{{ error.statusCode }}</h1>

    <p>{{ error.statusMessage }}</p>

    <UButton @click="clearError({ redirect: '/' })">
      بازگشت به خانه
    </UButton>
  </div>
</template>
