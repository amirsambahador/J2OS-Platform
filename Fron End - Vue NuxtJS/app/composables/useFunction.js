export const useFunction = () => {
  const x = ref(0)

  const f1 = () => {
    x.value++
    f2()
  }

  // اف 2 بعنوان فانکشن فقط تعریف شده ولی در return نیامده است پس از بیرون نمی توان مستقیم به آن دسترسی داشت
  const f2 = () => {
    alert(`This is f2 from composable function ${x.value}`)
  }

  return {
    x, f1
  }
}
