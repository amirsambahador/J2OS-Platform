<script setup>
const number = useNumber()
const persianNumber = ref()
const englishNumber = ref()
const serverNumber = ref()
const persianText = ref()
const englishText = ref()
onMounted(() => {
  number.setupPersianDoubleNumberBox('doubleBox', 36, 5, (val) => {
    persianText.value = number.getPersianWords(val)
    persianNumber.value = number.getPersianNumber(val)
    englishNumber.value = number.getEnglishNumber(persianNumber.value)
    serverNumber.value = number.getEnglishNumberWithoutCommas(englishNumber.value)// فرقی ندارد عدد فارسی باشد یا انگلیسی
    englishText.value = number.getEnglishWords(val)
  })
  number.setupDoubleNumberBox('persianDoubleBox', 36, 5, (val) => {
    persianText.value = number.getPersianWords(val)
    persianNumber.value = number.getPersianNumber(val)
    englishNumber.value = number.getEnglishNumber(persianNumber.value)
    serverNumber.value = number.getEnglishNumberWithoutCommas(englishNumber.value)// فرقی ندارد عدد فارسی باشد یا انگلیسی
    englishText.value = number.getEnglishWords(val)
  })

  number.setupNumberBox('numberBox', 36, (val) => {
    persianText.value = number.getPersianWords(val)
    persianNumber.value = number.getPersianNumber(val)
    englishNumber.value = number.getEnglishNumber(persianNumber.value)
    serverNumber.value = number.getEnglishNumberWithoutCommas(englishNumber.value)// فرقی ندارد عدد فارسی باشد یا انگلیسی
    englishText.value = number.getEnglishWords(val)
  })
  number.setupPersianNumberBox('persianNumberBox', 36, (val) => {
    persianText.value = number.getPersianWords(val)
    persianNumber.value = number.getPersianNumber(val)
    englishNumber.value = number.getEnglishNumber(persianNumber.value)
    serverNumber.value = number.getEnglishNumberWithoutCommas(englishNumber.value)// فرقی ندارد عدد فارسی باشد یا انگلیسی
    englishText.value = number.getEnglishWords(val)
  })
})

const setNumber = () => {
  document.getElementById('persianDoubleBox').value = 123123123
  // این تابع برای این صدا شده که رفتار ورود با کیبورد شبیه سازی شود و تکس باکس عدد را جدا سازی کند
  document.getElementById('persianDoubleBox').dispatchEvent(new Event('input'))
}
</script>

<template>
  <div>
    عدد صحیح لاتین:
    <UInput id="numberBox" dir="ltr"/>
    <br> <br>
    عدد صحیح فارسی:
    <UInput id="persianNumberBox" dir="ltr"/>
    <br> <br>
    عدد ممیزی فارسی تا 36 رقم ممیز:
    <UInput id="doubleBox" dir="ltr" />
    <br> <br>
    عدد ممیزی انگلیسی تا 36 رقم ممیز:
    <UInput id="persianDoubleBox" dir="ltr" />
    <br> <br>
    متن فارسی:
    {{ persianText }}
    <br> <br>
    متن انگلیسی:
    {{ englishText }}
    <br><br>
    عدد فارسی با کاما:
    {{ persianNumber }}
    <br><br>
    عدد لاتین با کاما:
    {{ englishNumber }}
    <br><br>
    عدد مناسب برای ارسال بر سرور!!!!!!:
    {{ serverNumber }}
    <br><br>
    <UButton @click="setNumber">
      مقدار دادن با جاوا اسکریپت
    </UButton>
  </div>
</template>
