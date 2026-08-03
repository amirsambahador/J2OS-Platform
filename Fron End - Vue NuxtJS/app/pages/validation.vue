<script setup>
const validator = useValidation()

// normalize=false (پیش‌فرض) — برنامه‌نویس با data-normalize تصمیم می‌گیرد
// validator.addPattern('iranianPlate', /^\d{2}[\u0600-\u06FF]{1}\d{3}-\d{2}$/)
// normalize=true — همیشه normalize می‌شود بدون نیاز به data-normalize
// validator.addPattern('zipCode', /^\d{5}$/, true)

async function register() {
  const data = validator.getFormData('registerForm')
  if (validator.validateForm('registerForm')) {
    if (data.password !== data.confirmPassword) {
      validator.addError('رمز عبور و تکرار آن یکسان نیست')
      return
    }
    console.log(new FormData(document.getElementById('registerForm')))
    alert('ثبت نام با موفقیت انجام شد!')
  }
}
</script>

<template>
  <UPageCard
    title="ثبت نام"
    variant="soft"
  >
    <form id="registerForm">
      <UInput
        name="age"
        data-title="سن"
        placeholder="سن"
        class="w-full lg:w-1/3"
      />
      <br><br>
      <UInput
        name="firstName"
        data-title="نام"
        placeholder="نام بین 2 الی 36"
        required
        minlength="2"
        maxlength="36"
        class="w-full lg:w-1/3"
      />
      <br><br>
      <UInput
        name="lastName"
        data-title="نام خانوادگی"
        placeholder="نام خانوادگی"
        required
        minlength="2"
        maxlength="36"
        class="w-full lg:w-1/3"
      />
      <br><br>
      <UInput
        name="username"
        data-title="نام کاربری"
        placeholder="نام کاربری"
        required
        minlength="6"
        maxlength="36"
        class="w-full lg:w-1/3"
      />
      <br><br>
      <UInput
        name="password"
        data-title="رمز عبور"
        placeholder="رمز عبور"
        required
        minlength="6"
        maxlength="36"
        type="password"
        class="w-full lg:w-1/3"
      />
      <br><br>
      <UInput
        name="confirmPassword"
        data-title="تکرار رمز عبور"
        placeholder="تکرار رمز عبور"
        required
        minlength="6"
        maxlength="36"
        type="password"
        class="w-full lg:w-1/3"
      />
      <br><br>

      <!-- بدون data-normalize — normalize نمی‌شود -->
      <UInput
        name="email"
        data-title="ایمیل"
        data-pattern="email"
        placeholder="example@email.com"
        required
        class="w-full lg:w-1/3"
      />
      <br><br>

      <!-- با data-normalize="true" — برنامه‌نویس تصمیم گرفته normalize شود -->
      <UInput
        name="mobile"
        data-title="موبایل"
        data-pattern="mobile"
        data-normalize="true"
        placeholder="09123456789"
        required
        class="w-full lg:w-1/3"
      />
      <br><br>
      <UInput
        name="birthDate"
        data-title="تاریخ تولد"
        data-pattern="date"
        data-normalize="false"
        placeholder="1403/05/15"
        required
        class="w-full lg:w-1/3"
      />
      <br><br>
      <UInput
        name="nationalCode"
        data-title="کد ملی"
        data-pattern="nationalCode"
        data-normalize="true"
        placeholder="کد ملی 10 رقمی"
        required
        class="w-full lg:w-1/3"
      />
      <br><br>

      <!-- iranianPlate: normalize=false در addPattern → بدون data-normalize نرمالایز نمی‌شود -->
      <UInput
        name="plate"
        data-title="پلاک"
        data-pattern="iranianPlate"
        placeholder="12ب345-67"
        class="w-full lg:w-1/3"
      />
      <br><br>

      <!-- postalCode: normalize=true در addPattern → حتی بدون data-normalize نرمالایز می‌شود -->
      <UInput
        name="postalCode"
        data-title="کد پستی"
        data-pattern="postalCode"
        placeholder="1234567890"
        class="w-full lg:w-1/3"
      />
      <br><br>

      <div
        v-if="validator.state.errors.length"
        class="mb-4"
      >
        <UAlert
          v-for="(err, i) in validator.state.errors"
          :key="i"
          color="error"
          :title="err"
          class="w-full lg:w-1/3 mt-2 bg-red-700 hover:bg-red-800 text-white"
        />
      </div>

      <UButton
        type="button"
        @click="register"
      >
        ثبت نام
      </UButton>
    </form>
  </UPageCard>
</template>
