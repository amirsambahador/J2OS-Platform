<script setup>
const formRef = ref()
const avatar = ref(null)
const tags = ref(['Vue', 'Nuxt'])
const active = ref(false)

const statuses = [
  { label: 'شروع نشده', value: 'todo' },
  { label: 'در حال انجام', value: 'doing' },
  { label: 'در انتظار', value: 'waiting' },
  { label: 'انجام شده', value: 'done' }
]

const suggestions = [
  'Vue',
  'Nuxt',
  'TypeScript',
  'JavaScript',
  'Node.js',
  'Laravel',
  'React'
]

const countries = [
  {
    label: 'ژاپن',
    value: 'jp'
  },
  {
    label: 'کشور آلمان',
    value: 'de'
  },
  {
    label: 'ایران',
    value: 'ir'
  }
]

async function onSubmit() {
  const formData = new FormData(formRef.value)

  if (avatar.value) {
    formData.set('avatar', avatar.value)
  }

  for (const [key, value] of formData.entries()) {
    console.log(key, value)
  }
  console.log('__________________________________')
  console.log(formData)
}

async function addNewItem(item) {
  alert(item)
}
</script>

<template>
  <UPageCard
    title="المنت های فونت"
    variant="soft"
  >
    <form
      ref="formRef"
      class="space-y-4"
    >
      <UFormField
        label="نام کوچک خود را وارد کنید"
        required
      >
        <UInput
          class="w-full lg:w-1/3"
          name="name"
          placeholder="نام کوچک"
        />
      </UFormField>

      <UFormField
        label="ایمیل خود را وارد کنید"
        required
      >
        <UInput
          class="w-full lg:w-1/3"
          name="email"
          type="email"
          placeholder="ایمیل"
        />
      </UFormField>

      <UFormField
        label="پسورد خود را وارد کنید"
        required
      >
        <UInput
          class="w-full lg:w-1/3"
          name="password"
          type="password"
          placeholder="پسورد"
        />
      </UFormField>

      <UFormField label="بیوگرافی خود را وارد کنید">
        <UTextarea
          name="bio"
          class="w-full lg:w-1/3"
          placeholder="بیوگرافی"
        />
      </UFormField>

      <UFormField
        label="کشور خود را انتخاب کنید"
        required
      >
        <USelect
          class="w-full lg:w-1/3"
          name="country"
          placeholder="کشور"
          :items="countries"
        />
      </UFormField>

      <UFormField label="تگ های خود را آزادانه انتخاب کنید و اینتر کنید">
        <UInputTags
          v-model="tags"
          dir="rtl"
          class="w-full lg:w-1/3"
        />
        <input
          type="hidden"
          name="tags"
          :value="JSON.stringify(tags)"
        >
      </UFormField>

      <UFormField label="تگ ها را تایپ کنید و از لیست انتخاب کنید، میتوانید چیزی انتخاب کنید که وجود ندارد">
        <UInputMenu
          v-model="tags"
          dir="rtl"
          class="w-full lg:w-1/3"
          :items="suggestions"
          multiple
          placeholder="تایپ کنید..."
        >
          <template #empty>
            موردی یافت نشد
          </template>
        </UInputMenu>
        <input
          type="hidden"
          name="tags"
          :value="JSON.stringify(tags)"
        >
      </UFormField>

      <UFormField label="تگ ها را تایپ کنید و از لیست انتخاب کنید، اجازه انتخاب خارج از لیست وجود ندارد">
        <UInputMenu
          v-model="tags"
          dir="rtl"
          class="w-full lg:w-1/3"
          :items="suggestions"
          multiple
          placeholder="تایپ کنید..."
          @keydown.enter.prevent="() => {}"
        >
          <template #empty>
            <div class="text-center py-2 text-sm text-gray-500">
              <p class="text-xs mt-1">
                لطفاً از تگ‌های موجود انتخاب کنید
              </p>
            </div>
          </template>
        </UInputMenu>
      </UFormField>

      <UFormField label="آیا این گزینه فعال باشد؟">
        <USwitch v-model="active" />

        <input
          type="hidden"
          name="active"
          :value="active"
        >
      </UFormField>

      <UFormField label="لطفا تیک تایید قوانین را قرار دهید">
        <UCheckbox
          name="accepted"
          label="من این قانون را تایید می کنم"
        />
      </UFormField>

      <UFormField
        label="از بین گزینه های موجود چند گزینه را جستجو کرده و انتخاب کنید، خصوصیت multiple یعنی کاربر می تواند چند گزینه انتخاب کند"
      >
        <USelectMenu
          v-model="tags"
          dir="rtl"
          class="w-full lg:w-1/3"
          :items="suggestions"
          multiple
          searchable
          create-item
          placeholder="گزینه های موجود"
          :search-input="{
            placeholder: 'جستجو کنید...'
          }"
          @create="addNewItem"
        >
          <template #create-item-label="{ item }">
            <span>➕ افزودن "{{ item }}"</span>
          </template>
        </USelectMenu>
        <input
          type="hidden"
          name="tags"
          :value="JSON.stringify(tags)"
        >
      </UFormField>

      <UFormField label="از بین گزینه های موجود جستوجو کنید و فقط یک مورد را انتخاب کنید">
        <USelectMenu
          dir="rtl"
          class="w-full lg:w-1/3"
          name="option"
          :items="statuses"
          placeholder="گزینه های موجود"
          :search-input="{
            placeholder: 'جستجو کنید...'
          }"
        >
          <template #empty>
            موردی یافت نشد
          </template>
        </USelectMenu>
      </UFormField>

      <UButton
        type="button"
        @click="onSubmit"
      >
        Submit
      </UButton>
    </form>
  </UPageCard>
</template>
