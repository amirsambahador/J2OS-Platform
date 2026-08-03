/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useNumber = () => {
  /////////////////////////////////////////////////////////////////
  // آغاز تبدیل اعداد به حروف (فارسی/انگلیسی) — با یک هسته‌ی مشترک
  const NUMBER_WORD_CONFIGS = {
    en: {
      ones: ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine'],
      teens: ['Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'],
      tens: ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'],
      hundreds: ['', 'One Hundred', 'Two Hundred', 'Three Hundred', 'Four Hundred', 'Five Hundred', 'Six Hundred', 'Seven Hundred', 'Eight Hundred', 'Nine Hundred'],
      thousands: ['', 'Thousand', 'Million', 'Billion', 'Trillion', 'Quadrillion', 'Quintillion', 'Sextillion', 'Septillion', 'Octillion', 'Nonillion', 'Decillion'],
      joiner: ' ',
      zeroWord: 'Zero',
      decimalWord: 'Point',
      overflowMessage: 'Number is too large to convert to words (exceeds supported magnitude).'
    },
    fa: {
      ones: ['', 'یک', 'دو', 'سه', 'چهار', 'پنج', 'شش', 'هفت', 'هشت', 'نه'],
      teens: ['ده', 'یازده', 'دوازده', 'سیزده', 'چهارده', 'پانزده', 'شانزده', 'هفده', 'هجده', 'نوزده'],
      tens: ['', '', 'بیست', 'سی', 'چهل', 'پنجاه', 'شصت', 'هفتاد', 'هشتاد', 'نود'],
      hundreds: ['', 'صد', 'دویست', 'سیصد', 'چهارصد', 'پانصد', 'ششصد', 'هفتصد', 'هشتصد', 'نهصد'],
      thousands: ['', 'هزار', 'میلیون', 'میلیارد', 'بیلیون', 'بیلیارد', 'تریلیون', 'تریلیارد', 'کوادریلیون', 'کوادریلیارد', 'کوینتیلیون', 'کوینتیلیارد'],
      joiner: ' و ',
      zeroWord: 'صفر',
      decimalWord: 'ممیز',
      overflowMessage: 'عدد برای تبدیل به حروف بسیار بزرگ است (خارج از محدوده‌ی پشتیبانی‌شده).'
    }
  }

  const numberToWords = (numStr, lang) => {
    if (!numStr)
      return ''

    const config = NUMBER_WORD_CONFIGS[lang]

    numStr = String(numStr)
      .replace(/,/g, '')
      .replace(/٫/g, '.')

    let [integerPart = '0', decimalPart] = numStr.split('.')

    integerPart = integerPart.replace(/^0+(?=\d)/, '') || '0'

    const digitWords = [config.zeroWord, ...config.ones.slice(1)]

    const threeDigitToWords = (nStr) => {
      nStr = nStr.padStart(3, '0')

      const h = Number(nStr[0])
      const t = Number(nStr[1])
      const o = Number(nStr[2])

      const result = []

      if (h)
        result.push(config.hundreds[h])

      if (t === 1) {
        result.push(config.teens[o])
      } else {
        if (t > 1)
          result.push(config.tens[t])

        if (o)
          result.push(config.ones[o])
      }

      return result.join(config.joiner)
    }

    const integerToWords = (str) => {
      str = str.replace(/^0+/, '') || '0'

      if (str === '0')
        return config.zeroWord

      const parts = []

      while (str.length) {
        parts.push(str.slice(-3))
        str = str.slice(0, -3)
      }

      if (parts.length > config.thousands.length) {
        throw new RangeError(config.overflowMessage)
      }

      const words = []

      for (let i = 0; i < parts.length; i++) {
        const num = Number(parts[i])

        if (!num)
          continue

        let text = threeDigitToWords(parts[i])

        if (config.thousands[i])
          text += ' ' + config.thousands[i]

        words.push(text)
      }

      return words.reverse().join(config.joiner)
    }

    const integerWords = integerToWords(integerPart)

    if (decimalPart === undefined || decimalPart === '')
      return integerWords

    const decimalWords = decimalPart
      .split('')
      .map(d => digitWords[Number(d)])
      .join(' ')

    return `${integerWords} ${config.decimalWord} ${decimalWords}`
  }

  const getEnglishWords = numStr => numberToWords(numStr, 'en')
  const getPersianWords = numStr => numberToWords(numStr, 'fa')
  // پایان تبدیل اعداد به حروف
  /////////////////////////////////////////////////////////////////
  // آغاز تبدیل بین اعداد فارسی/انگلیسی و فرمت با کاما
  const getPersianNumber = (str) => {
    if (!str)
      return ''

    str = String(str)
      .replace(/٫/g, '.')
      .replace(/[^\d.]/g, '')

    const firstDot = str.indexOf('.')
    if (firstDot !== -1) {
      str = str.slice(0, firstDot + 1)
        + str.slice(firstDot + 1).replace(/\./g, '')
    }

    let [integer, decimal] = str.split('.')

    integer = integer.replace(/^0+(?=\d)/, '')

    if (integer === '')
      integer = '0'

    const formatted = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    const result = decimal !== undefined
      ? `${formatted}٫${decimal}`
      : formatted

    return result.replace(/\d/g, d => '۰۱۲۳۴۵۶۷۸۹'[d])
  }

  const getEnglishNumberWithoutCommas = (str) => {
    if (!str)
      return ''

    let result = String(str)
      .replace(/,/g, '')
      .replace(/٫/g, '.')
      .replace(/[۰-۹]/g, d => '0123456789'['۰۱۲۳۴۵۶۷۸۹'.indexOf(d)])

    result = result.replace(/[^\d.]/g, '')

    const firstDot = result.indexOf('.')
    if (firstDot !== -1) {
      result = result.slice(0, firstDot + 1)
        + result.slice(firstDot + 1).replace(/\./g, '')
    }

    const [integer, decimal] = result.split('.')

    const cleanInteger = (integer || '').replace(/^0+(?=\d)/, '') || '0'

    return decimal !== undefined
      ? `${cleanInteger}.${decimal}`
      : cleanInteger
  }

  const getEnglishNumber = (str) => {
    if (!str)
      return ''

    const result = String(str)
      .replace(/٫/g, '.')
      .replace(/[۰-۹]/g, d => '0123456789'['۰۱۲۳۴۵۶۷۸۹'.indexOf(d)])

    const [integer = '', decimal] = result.split('.')

    const cleanInteger = integer
      .replace(/,/g, '')
      .replace(/^0+(?=\d)/, '') || '0'

    const formatted = cleanInteger.replace(/\B(?=(\d{3})+(?!\d))/g, ',')

    return decimal !== undefined
      ? `${formatted}٫${decimal}`
      : formatted
  }
  // پایان تبدیل بین اعداد فارسی/انگلیسی و فرمت با کاما
  /////////////////////////////////////////////////////////////////
  // تابع کمکی مشترک: حفظ موقعیت کرسر (caret) هنگام فرمت‌شدن مقدار ورودی
  const setInputValuePreservingCaret = (el, newValue) => {
    const prevLength = el.value.length
    const prevCaret = el.selectionStart ?? prevLength

    el.value = newValue

    const lengthDiff = newValue.length - prevLength
    const newCaret = Math.max(0, prevCaret + lengthDiff)

    if (document.activeElement === el) {
      el.setSelectionRange(newCaret, newCaret)
    }
  }
  /////////////////////////////////////////////////////////////////
  // آغاز ساخت جعبه‌های عددی — یک کارخانه‌ی مشترک به‌جای چهار تابع تقریباً
  // یکسان (فقط تفاوت در مجاز بودن اعشار و ارقام خروجی فارسی/انگلیسی)
  const toEnglishDigits = str =>
    String(str).replace(/[۰-۹]/g, d => '0123456789'['۰۱۲۳۴۵۶۷۸۹'.indexOf(d)])

  const toPersianDigits = str =>
    String(str).replace(/\d/g, d => '۰۱۲۳۴۵۶۷۸۹'[d])

  const createSetupNumberBox = ({ allowDecimal, digitsOutput }) => {
    return (inputId, maxLength = 36, decimalPlacesOrCallback, maybeCallback) => {
      const decimalPlaces = allowDecimal ? (decimalPlacesOrCallback ?? 36) : null
      const onChangeCallback = allowDecimal ? maybeCallback : decimalPlacesOrCallback

      const el = document.getElementById(inputId)
      if (!el) return

      const toInternal = str => toEnglishDigits(str).replace(/٫/g, '.')

      const format = allowDecimal
        ? (value) => {
            value = toInternal(value).replace(/[^\d.]/g, '')

            const firstDot = value.indexOf('.')
            if (firstDot !== -1) {
              value = value.slice(0, firstDot + 1)
                + value.slice(firstDot + 1).replace(/\./g, '')
            }

            let [integer = '', decimal] = value.split('.')
            integer = integer.slice(0, maxLength)
            integer = integer.replace(/^0+(?=\d)/, '') || '0'

            if (decimal === undefined)
              return integer

            decimal = decimal.slice(0, decimalPlaces)
            return integer + '.' + decimal
          }
        : (value) => {
            let raw = toInternal(value).replace(/\D/g, '').slice(0, maxLength)
            raw = raw.replace(/^0+(?=\d)/, '')
            if (raw === '')
              raw = '0'
            return raw
          }

      const withCommas = (value) => {
        const [integer, decimal] = value.split('.')
        const formatted = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
        return decimal !== undefined ? `${formatted}.${decimal}` : formatted
      }

      const toDisplay = (raw) => {
        const withSeparators = allowDecimal
          ? withCommas(raw).replace(/\./g, '٫')
          : withCommas(raw)

        return digitsOutput === 'fa' ? toPersianDigits(withSeparators) : withSeparators
      }

      const update = (value) => {
        const raw = format(value)

        el.dataset.raw = raw
        setInputValuePreservingCaret(el, toDisplay(raw))

        if (typeof onChangeCallback === 'function')
          onChangeCallback(raw)
      }

      const onInput = () => update(el.value)
      const onPaste = (e) => {
        e.preventDefault()
        update((e.clipboardData || window.clipboardData).getData('text'))
      }

      el.addEventListener('input', onInput)
      el.addEventListener('paste', onPaste)

      update(el.value)

      return () => {
        el.removeEventListener('input', onInput)
        el.removeEventListener('paste', onPaste)
      }
    }
  }

  const setupNumberBox = createSetupNumberBox({ allowDecimal: false, digitsOutput: 'en' })
  const setupPersianNumberBox = createSetupNumberBox({ allowDecimal: false, digitsOutput: 'fa' })
  const setupDoubleNumberBox = createSetupNumberBox({ allowDecimal: true, digitsOutput: 'en' })
  const setupPersianDoubleNumberBox = createSetupNumberBox({ allowDecimal: true, digitsOutput: 'fa' })
  // پایان ساخت جعبه‌های عددی

  return {
    getPersianWords,
    getPersianNumber,
    getEnglishNumber,
    getEnglishNumberWithoutCommas,
    setupNumberBox,
    setupPersianNumberBox,
    setupDoubleNumberBox,
    setupPersianDoubleNumberBox,
    getEnglishWords
  }
}
