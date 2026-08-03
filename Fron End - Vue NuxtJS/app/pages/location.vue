<template>
  <div>
    <div>
      {{ lat }}
    </div>
    <br>
    <div>
      {{ long }}
    </div>
    <UButton
      :disabled="!lat || !long"
      @click="getData"
    >
      CLICK!!!!
    </UButton>
  </div>
</template>

<script setup>
const location = useLocationAPI()
const lat = ref()
const long = ref()

async function getData() {
  alert(await location.getCountry(lat.value, long.value))
  alert(await location.getCity(lat.value, long.value))
  alert(await location.getPostCode(lat.value, long.value))
  alert(await location.getAddress(lat.value, long.value))
}

onMounted(() => {
  location.setupLocationListener(
    (x, y) => {
      lat.value = x
      long.value = y
    },
    (err) => {
      alert(err.message)
    }
  )
})
</script>
