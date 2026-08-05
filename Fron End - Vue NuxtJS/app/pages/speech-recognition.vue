<script setup>
const speech = useSpeechRecognitionAPI()

const fileName = ref('')
const result = ref('')
const isSupported = ref(true)
const isListening = ref(false)

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
  isListening.value = speech.isListening()
}

function onEnd() {
  isListening.value = false
}

function start() {
  speech.start()
  isListening.value = speech.isListening()
}

function stop() {
  speech.stop()
}

onBeforeUnmount(() => {
  speech.stop()
})

onDeactivated(() => {
  speech.stop()
})
</script>

<template>
  <div>
    <h1>تست Speech Recognition</h1>

    <template v-if="isSupported">
      <UButton
        :disabled="isListening"
        @click="start"
      >
        شروع
      </UButton>

      <UButton
        :disabled="!isListening"
        @click="stop"
      >
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

      <p v-if="isListening" class="text-green-500">
        در حال شنیدن...
      </p>
      <p v-else class="text-gray-400">
        متوقف شده
      </p>

      <p>
        {{ result }}
      </p>
    </template>

    <p v-else>
      {{ result }}
    </p>
  </div>
</template>
