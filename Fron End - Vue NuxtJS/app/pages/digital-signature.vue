<script setup>
const signature = useDigitalSignature()

onMounted(() => {
  signature.init('signature')
})

onBeforeUnmount(() => {
  signature.destroy()
})
</script>

<template>
  <div>
    <canvas
      id="signature"
      width="600"
      height="300"
      class="block mx-auto my-5 border bg-white"
    />
    <br><br>
    <p class="text-center">
      <UButton @click="signature.clear()">
        پاک کردن
      </UButton>

      <UButton @click="signature.save('test.png')">
        ذخیره
      </UButton>
    </p>
  </div>
</template>
