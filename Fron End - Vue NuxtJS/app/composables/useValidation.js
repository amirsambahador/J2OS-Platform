/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export function useValidation() {
  // ری اکتیور شده بخاطر اینکه در کد لازم نباشه .value بزنیم!
  const state = reactive({ errors: [] })

  const safePatterns = {
    // ───── ایران ─────
    mobile: { regex: /^09\d{9}$/, normalize: false }, // 09123456789
    nationalCode: { regex: /^\d{10}$/, normalize: false }, // 0123456789
    postalCode: { regex: /^\d{10}$/, normalize: false }, // 1234567890
    date: { regex: /^\d{4}\/\d{2}\/\d{2}$/, normalize: false }, // 1403/05/15
    iranianPlate: { regex: /^\d{2}[\u0600-\u06FF]\d{3}-\d{2}$/, normalize: false }, // 12ب345-67
    shebaNumber: { regex: /^IR\d{24}$/, normalize: false }, // IR123456789012345678901234
    cardNumber: { regex: /^\d{16}$/, normalize: false }, // 1234567890123456
    landline: { regex: /^0\d{10}$/, normalize: false }, // 02112345678
    taxCode: { regex: /^\d{11}$/, normalize: false }, // کد اقتصادی 11 رقمی
    companyNationalId: { regex: /^\d{11}$/, normalize: false }, // شناسه ملی شرکت

    // ───── عمومی ─────
    email: { regex: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, normalize: false }, // user@example.com
    url: { regex: /^https?:\/\/[^\s$.?#].[^\s]*$/, normalize: false }, // https://example.com
    numeric: { regex: /^\d+$/, normalize: false }, // فقط عدد
    decimal: { regex: /^\d+(\.\d+)?$/, normalize: false }, // 12.5
    integer: { regex: /^-?\d+$/, normalize: false }, // -5 یا 10
    positiveNumber: { regex: /^[1-9]\d*$/, normalize: false }, // عدد مثبت غیر صفر
    percentage: { regex: /^(100|[1-9]?\d)$/, normalize: false }, // 0 تا 100
    username: { regex: /^[a-zA-Z0-9_]{3,36}$/, normalize: false }, // حروف، عدد، آندراسکور
    password: { regex: /^(?=.*[A-Z])(?=.*\d).{8,}$/, normalize: false }, // حداقل 8 کاراکتر، عدد و حرف بزرگ
    strongPassword: { regex: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).{8,}$/, normalize: false }, // + کاراکتر خاص
    ipv4: { regex: /^(\d{1,3}\.){3}\d{1,3}$/, normalize: false }, // 192.168.1.1
    hexColor: { regex: /^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/, normalize: false }, // #fff یا #ffffff
    time: { regex: /^([01]\d|2[0-3]):[0-5]\d$/, normalize: false }, // 14:30
    gregorianDate: { regex: /^\d{4}-\d{2}-\d{2}$/, normalize: false }, // 2024-01-15
    slug: { regex: /^[a-z0-9]+(?:-[a-z0-9]+)*$/, normalize: false }, // my-page-title
    alphaEn: { regex: /^[a-zA-Z]+$/, normalize: false }, // فقط حروف انگلیسی
    alphaFa: { regex: /^[\u0600-\u06FF\s]+$/, normalize: false }, // فقط حروف فارسی
    alphaNumEn: { regex: /^[a-zA-Z0-9]+$/, normalize: false }, // حروف و عدد انگلیسی
    noSpecialChar: { regex: /^[a-zA-Z0-9\u0600-\u06FF\s]+$/, normalize: false }, // بدون کاراکتر خاص

    // ───── شبکه اجتماعی ─────
    instagram: { regex: /^@?[a-zA-Z0-9._]{1,30}$/, normalize: false }, // @username
    telegram: { regex: /^@?[a-zA-Z0-9_]{5,32}$/, normalize: false } // @username
  }

  function addPattern(key, regex, shouldNormalize = false) {
    if (!(regex instanceof RegExp)) {
      addError('pattern باید RegExp باشد')
      throw new Error('pattern باید RegExp باشد')
    }
    safePatterns[key] = { regex, normalize: shouldNormalize }
  }

  function clear() {
    state.errors.length = 0
  }

  function addError(msg) {
    if (msg) state.errors.push(msg)
  }

  function normalize(value, options = {}) {
    if (!value) return ''
    let str = value.toString().trim()
    if (options.removeSpaces) str = str.replace(/\s/g, '')
    return str.replace(/[۰-۹]/g, d => '0123456789'[d.charCodeAt(0) - 1776])
  }

  function normalizeInput(input) {
    const patternKey = input.dataset.pattern
    const patternNormalize = safePatterns[patternKey]?.normalize === true
    const shouldNormalize = input.dataset.normalize === 'true' || patternNormalize
    return shouldNormalize
      ? normalize(input.value, { removeSpaces: true })
      : (input.value || '').toString().trim()
  }

  function validateForm(formId) {
    clear()
    const form = document.getElementById(formId)
    if (!form) {
      addError('فرم پیدا نشد')
      return false
    }

    form.querySelectorAll('input, textarea, select').forEach((input) => {
      const title = input.dataset.title || input.name || 'فیلد'
      const value = normalizeInput(input)
      const required = input.required
      const minLength = Number(input.getAttribute('minlength') || 0)
      const maxLength = Number(input.getAttribute('maxlength') || 0)
      const patternKey = input.dataset.pattern

      if (required && !value) {
        addError(`مقداری برای فیلد ${title} مشخص نشده است`)
        return
      }
      if (!value) return

      if (minLength && value.length < minLength)
        addError(`مقدار وارد شده برای ${title} کوتاه است`)

      if (maxLength && value.length > maxLength)
        addError(`مقدار وارد شده برای ${title} طولانی است`)

      if (patternKey) {
        if (!safePatterns[patternKey])
          addError(`pattern با کلید ${patternKey} تعریف نشده است`)
        else if (!safePatterns[patternKey].regex.test(value))
          addError(`مقدار وارد شده برای ${title} معتبر نیست`)
      }
    })

    return state.errors.length === 0
  }

  function getFormData(formId) {
    const form = document.getElementById(formId)
    if (!form) return {}

    const data = {}
    form.querySelectorAll('input, textarea, select').forEach((input) => {
      if (!input.name) return
      data[input.name] = normalizeInput(input)
    })

    return data
  }

  return { state, clear, addError, addPattern, validateForm, getFormData }
}
