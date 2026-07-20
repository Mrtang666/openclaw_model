# Local SILK encoder

This wrapper uses the `silk-wasm` package to convert a WAV file to a WeChat
SILK V3 file:

```powershell
npm install
.\silk-encoder.cmd input.wav output.silk
```

The Java application invokes the same command through
`SPEECH_SILK_ENCODER_PATH`. Node.js must be available on `PATH`.
