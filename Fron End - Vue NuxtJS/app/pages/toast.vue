<script setup lang="ts">
const toast = useToast()

function showToast() {
  toast.add({
    title: 'تیتر پیام',
    description: 'محتوای پیام در این بخش قرار می گیرد.',
    duration: 0,
    actions: [{
      icon: 'i-lucide-reply',
      label: 'دکمه',
      color: 'primary',
      onClick: () => {
        toast.add({ title: 'Reply sent', color: 'success' })
      }
    }]
  })
}
</script>

<template>
  <UButton
    label="Show toast"
    @click="showToast"
  />
</template>
