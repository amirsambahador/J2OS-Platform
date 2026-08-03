/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useNetworkAPI = () => {
  const cache = new Map()

  const isInternetConnected = () => {
    return window.navigator.onLine
  }

  const getIP = async () => {
    const response = await fetch('https://api.ipify.org?format=json')
    if (!response.ok) {
      throw new Error('Failed to fetch IP address')
    }
    const data = await response.json()
    return data.ip
  }

  const getWhoIsAPI = (ip) => {
    if (cache.has(ip)) return cache.get(ip)

    const promise = (async () => {
      const response = await fetch(`https://ipwho.is/${ip}`)
      if (!response.ok) {
        cache.delete(ip)
        throw new Error('Failed to fetch IP information')
      }
      return response.json()
    })()

    cache.set(ip, promise)
    return promise
  }

  const getContinent = async (ip) => {
    return (await getWhoIsAPI(ip)).continent
  }

  const getCountry = async (ip) => {
    return (await getWhoIsAPI(ip)).country
  }

  const getCity = async (ip) => {
    return (await getWhoIsAPI(ip)).city
  }

  const getISP = async (ip) => {
    return (await getWhoIsAPI(ip)).connection.isp
  }

  const getOrganization = async (ip) => {
    return (await getWhoIsAPI(ip)).connection.org
  }

  const getDomain = async (ip) => {
    return (await getWhoIsAPI(ip)).connection.domain
  }

  const getEmoji = async (ip) => {
    return (await getWhoIsAPI(ip)).flag.emoji
  }

  const getLatitude = async (ip) => {
    return (await getWhoIsAPI(ip)).latitude
  }

  const getLongitude = async (ip) => {
    return (await getWhoIsAPI(ip)).longitude
  }

  const getDownloadSpeed = () => {
    return (
      navigator.connection
      || navigator.mozConnection
      || navigator.webkitConnection
    )?.downlink ?? null
  }

  const getPing = () => {
    return (
      navigator.connection
      || navigator.mozConnection
      || navigator.webkitConnection
    )?.rtt ?? null
  }

  const getNetworkType = () => {
    return (
      navigator.connection
      || navigator.mozConnection
      || navigator.webkitConnection
    )?.type ?? null
  }

  const getNetworkGeneration = () => {
    return (
      navigator.connection
      || navigator.mozConnection
      || navigator.webkitConnection
    )?.effectiveType ?? null
  }

  return {
    isInternetConnected,
    getIP,
    getContinent,
    getCountry,
    getCity,
    getISP,
    getOrganization,
    getDomain,
    getEmoji,
    getLatitude,
    getLongitude,
    getPing,
    getDownloadSpeed,
    getNetworkType,
    getNetworkGeneration
  }
}
