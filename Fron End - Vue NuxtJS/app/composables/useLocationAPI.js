/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useLocationAPI = () => {
  const cache = new Map()

  const setupLocationListener = (onSuccess, onError) => {
    if (!('geolocation' in navigator)) {
      const err = new Error('مرورگر از مختصات جغرافیایی پشتیبانی نمی‌کند')
      if (typeof onError === 'function') {
        onError(err)
        return
      }
      throw err
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        onSuccess(position.coords.latitude, position.coords.longitude)
      },
      (error) => {
        const message = `خطا در دریافت اطلاعات از سنسور مختصات جغرافیایی: ${error.message}`
        if (typeof onError === 'function') {
          onError(new Error(message))
        } else {
          console.error(message)
        }
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 0
      }
    )
  }

  const getAddress = async (latitude, longitude) => {
    const response = await getOpenStreetMapAPI(latitude, longitude)
    return response.display_name
  }

  const getCountry = async (latitude, longitude) => {
    const response = await getOpenStreetMapAPI(latitude, longitude)
    return response.address.country
  }

  const getCity = async (latitude, longitude) => {
    const response = await getOpenStreetMapAPI(latitude, longitude)
    return response.address.city
  }

  const getPostCode = async (latitude, longitude) => {
    const response = await getOpenStreetMapAPI(latitude, longitude)
    return response.address.postcode
  }

  const getOpenStreetMapAPI = (latitude, longitude) => {
    const key = `${latitude},${longitude}`

    if (cache.has(key)) return cache.get(key)

    const promise = (async () => {
      const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${latitude}&lon=${longitude}`
      const response = await fetch(url, {
        headers: {
          Accept: 'application/json'
        }
      })
      if (!response.ok) {
        cache.delete(key)
        throw new Error('خطا در دریافت اطلاعات')
      }
      return response.json()
    })()

    cache.set(key, promise)
    return promise
  }

  return {
    setupLocationListener, getAddress, getCountry, getPostCode, getCity
  }
}
