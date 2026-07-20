import { readFile, writeFile } from "node:fs/promises";
import { encode, isWav } from "silk-wasm";

const [, , inputPath, outputPath] = process.argv;
if (!inputPath || !outputPath) {
  console.error("Usage: silk-encoder input.wav output.silk");
  process.exit(2);
}

try {
  const input = normalizeWav(await readFile(inputPath));
  if (!isWav(input)) {
    throw new Error("input is not a valid WAV file");
  }
  const result = await encode(input, 0);
  if (!result.data || result.data.length === 0) {
    throw new Error("encoder returned empty SILK data");
  }
  await writeFile(outputPath, result.data);
  process.stdout.write(`durationMs=${result.duration}\n`);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}

function normalizeWav(source) {
  const data = Buffer.from(source);
  if (data.length < 12
      || data.toString("ascii", 0, 4) !== "RIFF"
      || data.toString("ascii", 8, 12) !== "WAVE") {
    return data;
  }
  data.writeUInt32LE(data.length - 8, 4);
  let offset = 12;
  while (offset + 8 <= data.length) {
    const chunkId = data.toString("ascii", offset, offset + 4);
    const declaredLength = data.readUInt32LE(offset + 4);
    const availableLength = Math.max(0, data.length - offset - 8);
    if (chunkId === "data" || declaredLength > availableLength) {
      data.writeUInt32LE(availableLength, offset + 4);
      break;
    }
    offset += 8 + declaredLength + (declaredLength % 2);
  }
  return data;
}
