/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useDigitalSignature = () => {
  let canvas
  let ctx
  let drawing = false

  function getPosition(event) {
    const rect = canvas.getBoundingClientRect()

    if (event.touches) {
      return {
        x: event.touches[0].clientX - rect.left,
        y: event.touches[0].clientY - rect.top
      }
    }

    return {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top
    }
  }

  function start(event) {
    drawing = true

    const position = getPosition(event)

    ctx.beginPath()
    ctx.moveTo(position.x, position.y)

    event.preventDefault()
  }

  function draw(event) {
    if (!drawing)
      return

    const position = getPosition(event)

    ctx.lineTo(position.x, position.y)
    ctx.stroke()

    event.preventDefault()
  }

  function end() {
    drawing = false
  }

  function init(canvasId) {
    canvas = document.getElementById(canvasId)

    if (!canvas) {
      throw new Error(`Canvas با id="${canvasId}" پیدا نشد.`)
    }

    ctx = canvas.getContext('2d')

    ctx.lineWidth = 2
    ctx.lineCap = 'round'
    ctx.strokeStyle = '#000'

    canvas.addEventListener('mousedown', start)
    canvas.addEventListener('mousemove', draw)
    canvas.addEventListener('mouseup', end)
    canvas.addEventListener('mouseleave', end)
    canvas.addEventListener('touchstart', start, { passive: false })
    canvas.addEventListener('touchmove', draw, { passive: false })
    canvas.addEventListener('touchend', end, { passive: false })
  }

  function clear() {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.beginPath()
  }

  function save(fileName = 'signature.png') {
    const image = canvas.toDataURL('image/png')

    const link = document.createElement('a')

    link.href = image
    link.download = fileName
    link.click()
  }

  function destroy() {
    if (!canvas) return

    canvas.removeEventListener('mousedown', start)
    canvas.removeEventListener('mousemove', draw)
    canvas.removeEventListener('mouseup', end)
    canvas.removeEventListener('mouseleave', end)

    canvas.removeEventListener('touchstart', start, { passive: false })
    canvas.removeEventListener('touchmove', draw, { passive: false })
    canvas.removeEventListener('touchend', end, { passive: false })

    canvas = null
    ctx = null
  }

  return {
    init,
    clear,
    save,
    destroy
  }
}
