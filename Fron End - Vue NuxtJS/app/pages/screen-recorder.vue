<script setup>
const recorder = useScreenRecorder()
const visibility = ref('none')
const toast = useToast()

onMounted(() => {
  recorder.init('preview', true, true, () => {
    visibility.value = 'none'
  })
})

const start = async () => {
  try {
    await recorder.start()
    visibility.value = 'block'
  } catch (e) {
    console.error(e.message, e.cause)
    toast.add({ title: 'خطا در شروع ضبط', description: e.message, color: 'error' })
  }
}

const stop = () => {
  try {
    recorder.stop('test.webm')
  } catch (e) {
    console.error(e.message)
    toast.add({ title: 'خطا', description: e.message, color: 'error' })
  }
}
</script>

<template>
  <div class="w-fit mx-auto">
    <div v-if="visibility === 'none'">
      برای شروع ضبط روی دکمه «شروع ضبط» کلیک کنید.
    </div>

    <video
      id="preview"
      width="700"
      autoplay
      muted
      :style="{ display: visibility }"
      class="block border"
    />

    <div class="flex justify-center gap-2 mt-4">
      <UButton @click="start">
        شروع ضبط
      </UButton>

      <UButton @click="stop">
        پایان ضبط
      </UButton>
    </div>
  </div>
</template>
