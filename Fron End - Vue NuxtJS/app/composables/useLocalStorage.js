/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useLocalStorage = () => {
  let storage = null

  try {
    const testKey = '__storage_test__'
    window.localStorage.setItem(testKey, '1')
    window.localStorage.removeItem(testKey)
    storage = window.localStorage
  } catch {
    storage = null
  }

  const ensureSupported = () => {
    if (!storage) {
      throw new Error('عدم پشتیبانی مرورگر از دیتابیس محلی')
    }
  }

  const persist = (key, value) => {
    ensureSupported()
    try {
      storage.setItem(key, value)
    } catch (err) {
      throw new Error(`ذخیره‌سازی ناموفق بود: ${err.message}`, { cause: err })
    }
  }

  const removeByKey = (key) => {
    ensureSupported()
    storage.removeItem(key)
  }

  const findByKey = (key) => {
    ensureSupported()
    return storage.getItem(key)
  }

  const getKeys = () => {
    ensureSupported()
    return Array.from({ length: storage.length }, (_, i) => storage.key(i))
  }

  return {
    persist, removeByKey, findByKey, getKeys
  }
}
