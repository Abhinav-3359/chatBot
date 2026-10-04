// AudioWorkletProcessor that turns the microphone's native-rate audio
// into exactly what the voice server expects: 16kHz mono, 20ms frames
// (320 samples), signed 16-bit PCM - matching voice_socket.py's VAD and
// the Python test client's protocol exactly, so no server changes are
// needed to talk to it from the browser.
//
// Runs on the audio rendering thread, not the main thread, so it can't
// import app code - this file is plain JS loaded via audioWorklet.addModule().

const TARGET_SAMPLE_RATE = 16000;
const CHUNK_SIZE = 320; // 20ms @ 16kHz

class PCMRecorderProcessor extends AudioWorkletProcessor {
  constructor() {
    super();

    // sampleRate is a global in the AudioWorkletGlobalScope - the
    // context's native rate (commonly 44100 or 48000, browser/device
    // dependent, never assume it's already 16000).
    this.resampleRatio = sampleRate / TARGET_SAMPLE_RATE;

    this.inputBuffer = [];
    this.outputBuffer = [];
    this.resamplePos = 0;
  }

  process(inputs) {
    const input = inputs[0];
    const channel = input && input[0];

    if (!channel || channel.length === 0) {
      return true;
    }

    for (let i = 0; i < channel.length; i++) {
      this.inputBuffer.push(channel[i]);
    }

    // Linear-interpolation downsample to 16kHz - simple, cheap, and
    // good enough for speech; a full windowed-sinc resampler would be
    // overkill here.
    while (true) {
      const idx = Math.floor(this.resamplePos);

      if (idx + 1 >= this.inputBuffer.length) {
        break;
      }

      const frac = this.resamplePos - idx;
      const sample =
        this.inputBuffer[idx] * (1 - frac) + this.inputBuffer[idx + 1] * frac;

      this.outputBuffer.push(sample);
      this.resamplePos += this.resampleRatio;

      if (this.outputBuffer.length >= CHUNK_SIZE) {
        const chunk = this.outputBuffer.splice(0, CHUNK_SIZE);
        const int16 = new Int16Array(CHUNK_SIZE);

        for (let i = 0; i < CHUNK_SIZE; i++) {
          const s = Math.max(-1, Math.min(1, chunk[i]));
          int16[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
        }

        this.port.postMessage(int16.buffer, [int16.buffer]);
      }
    }

    // Drop the portion of inputBuffer already consumed by resampling,
    // so it doesn't grow without bound.
    const consumed = Math.floor(this.resamplePos);

    if (consumed > 0) {
      this.inputBuffer.splice(0, consumed);
      this.resamplePos -= consumed;
    }

    return true;
  }
}

registerProcessor("pcm-recorder-processor", PCMRecorderProcessor);
