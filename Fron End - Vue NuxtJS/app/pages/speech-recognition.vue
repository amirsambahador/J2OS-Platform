<script setup>
const speech = useSpeechRecognitionAPI()

const fileName = ref('')
const result = ref('')
const isSupported = ref(true)

onMounted(() => {
  try {
    speech.init(onResult, onError, onEnd)
  } catch (err) {
    isSupported.value = false
    result.value = err.message
  }
})

function onResult(time, text, isFinal) {
  result.value = `[${time}] ${text} (${isFinal ? 'نهایی' : 'موقت'})`
}

function onError(error) {
  result.value = error
}

function onEnd() {
  console.log('END')
}

onBeforeUnmount(() => {
  speech.stop()
})
</script>

<template>
  <div>
    <h1>تست Speech Recognition</h1>

    <template v-if="isSupported">
      <UButton @click="speech.start()">
        شروع
      </UButton>

      <UButton @click="speech.stop()">
        توقف
      </UButton>

      <UButton @click="speech.exportFile(fileName)">
        خروجی
      </UButton>

      <UButton @click="speech.clear()">
        پاک کردن
      </UButton>

      <br><br>

      <UInput
        v-model="fileName"
        placeholder="نام فایل"
      />
      <p>
        {{ result }}
      </p>
    </template>

    <p v-else>
      {{ result }}
    </p>
  </div>
</template>
