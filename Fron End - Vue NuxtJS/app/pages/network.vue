<script setup>
const network = useNetworkAPI()

const status = ref()

const ip = ref()
const continent = ref()
const country = ref()
const city = ref()
const isp = ref()
const organization = ref()
const domain = ref()
const emoji = ref()
const latitude = ref()
const longitude = ref()

const ping = ref()
const downloadSpeed = ref()
const networkType = ref()
const networkGeneration = ref()

onMounted(async () => {
  status.value = network.isInternetConnected()

  ip.value = await network.getIP()

  continent.value = await network.getContinent(ip.value)
  country.value = await network.getCountry(ip.value)
  city.value = await network.getCity(ip.value)
  isp.value = await network.getISP(ip.value)
  organization.value = await network.getOrganization(ip.value)
  domain.value = await network.getDomain(ip.value)
  emoji.value = await network.getEmoji(ip.value)
  latitude.value = await network.getLatitude(ip.value)
  longitude.value = await network.getLongitude(ip.value)

  ping.value = network.getPing()// فقط روی موبایل
  downloadSpeed.value = network.getDownloadSpeed()// فقط روی موبایل
  networkType.value = network.getNetworkType()// فقط روی موبایل
  networkGeneration.value = network.getNetworkGeneration()// فقط روی موبایل
})
</script>

<template>
  <div>
    <p>Internet: {{ status }}</p>
    <p>IP: {{ ip }}</p>
    <p>Continent: {{ continent }}</p>
    <p>Country: {{ country }}</p>
    <p>City: {{ city }}</p>
    <p>ISP: {{ isp }}</p>
    <p>Organization: {{ organization }}</p>
    <p>Domain: {{ domain }}</p>

    <p>
      Emoji:
      <span style="font-size: 40px">{{ emoji }}</span>
    </p>

    <p>Latitude: {{ latitude }}</p>
    <p>Longitude: {{ longitude }}</p>

    <hr>

    <p>Ping: {{ ping }} ms</p>
    <p>Download Speed: {{ downloadSpeed }} Mbps</p>
    <p>Network Type: {{ networkType }}</p>
    <p>Network Generation: {{ networkGeneration }}</p>
  </div>
</template>
