/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useScreenRecorder = () => {
  let recorder
  let displayStream
  let micStream
  let combinedStream
  let audioContext
  let chunks = []
  let video
  let useMic = false
  let useSystemAudio = false
  let onRecordingEnded = null
  let pendingFileName = 'screen-record.webm'

  const init = (videoId, captureMic = false, captureSystemAudio = false, onEnded = null) => {
    video = document.getElementById(videoId)

    if (!video) {
      throw new Error(`Video با id="${videoId}" پیدا نشد.`)
    }

    useMic = captureMic
    useSystemAudio = captureSystemAudio
    onRecordingEnded = onEnded
  }

  const finalizeRecording = () => {
    const blob = new Blob(chunks, { type: 'video/webm' })
    const url = URL.createObjectURL(blob)

    const link = document.createElement('a')
    link.href = url
    link.download = pendingFileName
    link.click()

    URL.revokeObjectURL(url)

    chunks = []

    displayStream?.getTracks().forEach(track => track.stop())
    micStream?.getTracks().forEach(track => track.stop())
    audioContext?.close()

    displayStream = null
    micStream = null
    audioContext = null
    combinedStream = null

    onRecordingEnded?.()
  }

  const start = async () => {
    if (!video) {
      throw new Error('ویدیوی پیش‌نمایش تنظیم نشده است. ابتدا init را صدا بزنید.')
    }

    if (recorder && recorder.state === 'recording') {
      throw new Error('یک ضبط از قبل در حال اجراست')
    }

    chunks = []
    pendingFileName = 'screen-record.webm'

    try {
      displayStream = await navigator.mediaDevices.getDisplayMedia({
        video: true,
        audio: useSystemAudio
      })
    } catch (e) {
      throw new Error('دسترسی به صفحه لغو شد یا با خطا مواجه شد', { cause: e })
    }

    if (useMic) {
      try {
        micStream = await navigator.mediaDevices.getUserMedia({
          audio: true
        })
      } catch (e) {
        displayStream.getTracks().forEach(track => track.stop())
        displayStream = null
        throw new Error('دسترسی به میکروفون رد شد', { cause: e })
      }
    }

    const systemAudioTracks = useSystemAudio ? displayStream.getAudioTracks() : []
    const needsMixing = useMic && systemAudioTracks.length > 0

    let finalAudioTracks = []

    if (needsMixing) {
      audioContext = new AudioContext()
      const destination = audioContext.createMediaStreamDestination()

      audioContext.createMediaStreamSource(new MediaStream(systemAudioTracks)).connect(destination)
      audioContext.createMediaStreamSource(micStream).connect(destination)

      finalAudioTracks = destination.stream.getAudioTracks()
    } else if (useMic) {
      finalAudioTracks = micStream.getAudioTracks()
    } else if (systemAudioTracks.length > 0) {
      finalAudioTracks = systemAudioTracks
    }

    combinedStream = new MediaStream([
      ...displayStream.getVideoTracks(),
      ...finalAudioTracks
    ])

    video.srcObject = displayStream
    video.muted = true

    recorder = new MediaRecorder(combinedStream)

    recorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) {
        chunks.push(event.data)
      }
    }

    // Registered once, here — not inside stop() — so it fires no matter
    // which path (explicit stop(), or the track-ended listener below)
    // triggers recorder.stop().
    recorder.onstop = finalizeRecording

    // The user can end screen sharing from the browser's own native
    // "Stop sharing" control at any time, entirely outside this module's
    // control. Without this listener that event is invisible to us: the
    // combined stream may still hold a live audio track, so MediaRecorder
    // will keep "recording" against a dead video track indefinitely.
    // Driving stop() from here guarantees deterministic teardown either way.
    const [displayVideoTrack] = displayStream.getVideoTracks()
    displayVideoTrack.addEventListener('ended', () => {
      if (recorder && recorder.state !== 'inactive') {
        recorder.stop()
      }
    })

    recorder.start()
  }

  const stop = (fileName = 'screen-record.webm') => {
    if (!recorder) {
      throw new Error('هیچ ضبطی برای توقف وجود ندارد')
    }

    pendingFileName = fileName
    recorder.stop()
  }

  return {
    init,
    start,
    stop
  }
}
