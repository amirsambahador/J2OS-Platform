<script setup>
const date = useDate()

const validBirthDate = ref('')
const validEventDate = ref('')

onMounted(() => {
  // تاریخ تولد: بدون مقدار پیش‌فرض، کاربر باید خودش وارد کند
  date.setupDateBox('birthDateBox',
    1300, 1, 1,
    1410, 1, 1,
    (val) => { validBirthDate.value = val }
  )

  // تاریخ رویداد: با مقدار پیش‌فرض (امروز)
  date.setupDateBox('eventDateBox',
    1365, 7, 22,
    1410, 1, 1,
    (val) => { validEventDate.value = val },
    date.getPersianDate(date.getGregorianNowDate(), '-', '/')
  )
})

function getValues() {
  alert('تاریخ تولد:' + validBirthDate.value)
  alert('تاریخ رویداد:' + validEventDate.value)
  alert('تاریخ میلادی:' + date.getGregorianDate(validEventDate.value, '/', ','))
}
</script>

<template>
  <div tabindex="0">
    <div dir="ltr">
      <UInput
        id="birthDateBox"
        tabindex="1"
        required
      />
    </div>

    <div dir="ltr">
      <UInput
        id="eventDateBox"
        tabindex="2"
        required
      />
    </div>

    <UButton @click="getValues">
      CLICK
    </UButton>
  </div>
</template>
