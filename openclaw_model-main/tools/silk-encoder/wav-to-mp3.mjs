import { readFile, writeFile } from "node:fs/promises";
import { Mp3Encoder } from "@breezystack/lamejs";

const [, , inputPath, outputPath] = process.argv;
if (!inputPath || !outputPath) {
  console.error("Usage: wav-to-mp3 input.wav output.mp3");
  process.exit(2);
}

try {
  const wav = normalizeWav(await readFile(inputPath));
  const info = parseWav(wav);
  if (info.formatCode !== 1 || info.bitsPerSample !== 16) {
    throw new Error("only 16-bit PCM WAV is supported");
  }
  const encoder = new Mp3Encoder(info.channels, info.sampleRate, 64);
  const output = [];
  const frameSamples = 1152;
  for (let offset = 0; offset < info.samplesPerChannel; offset += frameSamples) {
    const count = Math.min(frameSamples, info.samplesPerChannel - offset);
    const left = new Int16Array(count);
    const right = info.channels === 2 ? new Int16Array(count) : undefined;
    for (let index = 0; index < count; index++) {
      const sampleOffset = info.dataOffset + (offset + index) * info.channels * 2;
      left[index] = wav.readInt16LE(sampleOffset);
      if (right) {
        right[index] = wav.readInt16LE(sampleOffset + 2);
      }
    }
    const encoded = encoder.encodeBuffer(left, right);
    if (encoded.length > 0) output.push(Buffer.from(encoded));
  }
  const tail = encoder.flush();
  if (tail.length > 0) output.push(Buffer.from(tail));
  const mp3 = Buffer.concat(output);
  if (mp3.length === 0) throw new Error("MP3 encoder returned empty data");
  await writeFile(outputPath, mp3);
  process.stdout.write(`bytes=${mp3.length}\n`);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}

function parseWav(wav) {
  if (wav.length < 44 || wav.toString("ascii", 0, 4) !== "RIFF"
      || wav.toString("ascii", 8, 12) !== "WAVE") {
    throw new Error("input is not a valid WAV file");
  }
  let offset = 12;
  let fmt;
  let dataOffset = -1;
  let dataLength = 0;
  while (offset + 8 <= wav.length) {
    const id = wav.toString("ascii", offset, offset + 4);
    const length = wav.readUInt32LE(offset + 4);
    if (id === "fmt ") {
      fmt = {
        formatCode: wav.readUInt16LE(offset + 8),
        channels: wav.readUInt16LE(offset + 10),
        sampleRate: wav.readUInt32LE(offset + 12),
        bitsPerSample: wav.readUInt16LE(offset + 22),
      };
    } else if (id === "data") {
      dataOffset = offset + 8;
      dataLength = Math.min(length, wav.length - dataOffset);
      break;
    }
    offset += 8 + length + (length % 2);
  }
  if (!fmt || dataOffset < 0 || fmt.channels < 1 || fmt.channels > 2) {
    throw new Error("WAV fmt/data chunk is missing or unsupported");
  }
  return {
    ...fmt,
    dataOffset,
    samplesPerChannel: Math.floor(dataLength / (fmt.channels * 2)),
  };
}

function normalizeWav(source) {
  const data = Buffer.from(source);
  if (data.length < 12 || data.toString("ascii", 0, 4) !== "RIFF") return data;
  data.writeUInt32LE(data.length - 8, 4);
  let offset = 12;
  while (offset + 8 <= data.length) {
    const id = data.toString("ascii", offset, offset + 4);
    const declared = data.readUInt32LE(offset + 4);
    const available = Math.max(0, data.length - offset - 8);
    if (id === "data" || declared > available) {
      data.writeUInt32LE(available, offset + 4);
      break;
    }
    offset += 8 + declared + (declared % 2);
  }
  return data;
}
