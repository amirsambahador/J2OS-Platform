<script setup>
const fullscreen = useFullscreen()

onMounted(() => {
  fullscreen.init('box')
})
</script>

<template>
  <div>
    <div
      id="box"
      style="
      width:600px;
      height:300px;
      margin:auto;
      background:#2196F3;
      color:white;
      display:flex;
      align-items:center;
      justify-content:center;
    "
    >
      این بخش Fullscreen می‌شود.
      <UButton
        class="ms-2"
        @click="fullscreen.close()"
      >
        خروج
      </UButton>
    </div>

    <p class="text-center mt-4">
      <UButton @click="fullscreen.open()">
        ورود به حالت تمام صفحه
      </UButton>
    </p>
  </div>
</template>
