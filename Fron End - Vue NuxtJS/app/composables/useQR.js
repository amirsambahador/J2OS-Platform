/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useQR = () => {
  const render = (tagId, url) => {
    const tag = document.getElementById(tagId)

    if (!tag) {
      throw new Error(`Element با id="${tagId}" پیدا نشد.`)
    }

    tag.innerHTML = ''

    new QRCode(tag, {
      text: url,
      width: 256,
      height: 256,
      colorDark: '#000000',
      colorLight: '#ffffff',
      correctLevel: QRCode.CorrectLevel.H
    })
  }

  const download = (tagId) => {
    const tag = document.getElementById(tagId)

    if (!tag) {
      throw new Error(`Element با id="${tagId}" پیدا نشد.`)
    }

    const img = tag.querySelector('img')
    const canvas = tag.querySelector('canvas')

    const dataUrl = img ? img.src : canvas ? canvas.toDataURL('image/png') : null

    if (!dataUrl) {
      throw new Error('QR Code یافت نشد؛ ابتدا باید render شده باشد.')
    }

    const link = document.createElement('a')
    link.href = dataUrl
    link.download = 'QR.png'
    link.click()
  }

  return {
    render, download
  }
}
