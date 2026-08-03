/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useDate = () => {
  const setupDateBox = (
    inputId,
    fromDateYear, fromDateMonth, fromDateDay,
    toDateYear, toDateMonth, toDateDay,
    onChange,
    initialValue // اختیاری: رشته‌ی شمسی 'YYYY/MM/DD' یا رشته‌ی 'today'
  ) => {
    const input = document.getElementById(inputId)

    const MIN_DATE = [fromDateYear, fromDateMonth, fromDateDay]
    const MAX_DATE = [toDateYear, toDateMonth, toDateDay]

    const toComparable = ([y, m, d]) => y * 10000 + m * 100 + d

    const mask = IMask(input, {
      mask: '0000/00/00', lazy: false, placeholderChar: '_'
    })

    const isValidJalaaliDay = (y, m, d) => {
      if (m < 1 || m > 12) return false
      const maxDay = jalaali.jalaaliMonthLength(y, m)
      return d >= 1 && d <= maxDay
    }

    input.addEventListener('input', () => {
      const val = input.value
      if (/^\d{4}\/\d{2}\/\d{2}$/.test(val)) {
        const [y, m, d] = val.split('/').map(Number)

        const isOutOfDayRange = !isValidJalaaliDay(y, m, d)
        const isBefore = toComparable([y, m, d]) < toComparable(MIN_DATE)
        const isAfter = toComparable([y, m, d]) > toComparable(MAX_DATE)

        if (isOutOfDayRange || isBefore || isAfter) {
          mask.value = ''
        }
      }
    })

    input.addEventListener('blur', () => {
      const val = input.value
      if (val.includes('_') || !/^\d{4}\/\d{2}\/\d{2}$/.test(val)) {
        mask.value = ''
      }
    })

    if (typeof onChange === 'function') {
      mask.on('accept', () => {
        onChange(mask.masked.isComplete ? mask.value : '')
      })
    }

    if (initialValue) {
      mask.value = initialValue === 'today' ? getTodayPersianDate() : initialValue
    }

    return mask
  }

  const getPersianDate = (dateString, inputSeparator, outputSeparator) => {
    if (!dateString)
      return ''

    const [gy, gm, gd] = dateString.split(inputSeparator).map(Number)

    const j = jalaali.toJalaali(gy, gm, gd)

    return `${j.jy}${outputSeparator}${String(j.jm).padStart(2, '0')}${outputSeparator}${String(j.jd).padStart(2, '0')}`
  }

  const getGregorianDate = (dateString, inputSeparator, outputSeparator) => {
    if (!dateString)
      return ''

    const [jy, jm, jd] = dateString.split(inputSeparator).map(Number)

    const g = jalaali.toGregorian(jy, jm, jd)

    return `${g.gy}${outputSeparator}${String(g.gm).padStart(2, '0')}${outputSeparator}${String(g.gd).padStart(2, '0')}`
  }

  const getGregorianNowDate = () => {
    const now = new Date()
    const y = now.getFullYear()
    const m = String(now.getMonth() + 1).padStart(2, '0')
    const d = String(now.getDate()).padStart(2, '0')
    return `${y}-${m}-${d}`
  }

  const getTodayPersianDate = () => {
    return getPersianDate(getGregorianNowDate(), '-', '/')
  }

  return {
    setupDateBox, getPersianDate, getGregorianNowDate, getGregorianDate
  }
}
