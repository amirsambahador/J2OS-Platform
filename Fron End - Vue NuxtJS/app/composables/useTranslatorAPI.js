/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useTranslatorAPI = () => {
  const translate = async (text, from, to) => {
    if (!text) return ''

    const url = `https://api.mymemory.translated.net/get?q=${encodeURIComponent(text)}&langpair=${encodeURIComponent(from)}|${encodeURIComponent(to)}`

    const res = await fetch(url)

    if (!res.ok) {
      throw new Error('خطا در دریافت ترجمه')
    }

    const data = await res.json()

    if (data.responseStatus !== 200) {
      throw new Error(data.responseDetails || 'ترجمه ناموفق بود')
    }

    return data.responseData.translatedText
  }

  return {
    translate
  }
}
