import {readdir, readFile, rm, writeFile} from 'node:fs/promises'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {promisify} from 'node:util'
import * as zlib from 'node:zlib'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDir, '..')
const distDir = path.resolve(frontendRoot, process.argv[2] ?? 'dist/grimmory/browser')

const minimumBytes = 1024
const requestedConcurrency = Number(process.env.PRECOMPRESS_CONCURRENCY || 8)
const concurrency = Number.isFinite(requestedConcurrency) && requestedConcurrency > 0
  ? Math.floor(requestedConcurrency)
  : 8
const textExtensions = new Set(['.css', '.html', '.js', '.json', '.mjs', '.svg', '.txt', '.webmanifest', '.xml'])
const compressibleExtensions = new Set([...textExtensions, '.wasm'])
const gzipAsync = promisify(zlib.gzip)
const brotliCompressAsync = promisify(zlib.brotliCompress)
const zstdCompressAsync = typeof zlib.zstdCompress === 'function' ? promisify(zlib.zstdCompress) : null

async function* walk(directory) {
  for (const entry of await readdir(directory, {withFileTypes: true})) {
    const fullPath = path.join(directory, entry.name)

    if (entry.isDirectory()) {
      yield* walk(fullPath)
      continue
    }

    if (entry.isFile()) {
      yield fullPath
    }
  }
}

function isCompressible(filePath) {
  return compressibleExtensions.has(path.extname(filePath).toLowerCase())
}

function brotliModeFor(filePath) {
  return textExtensions.has(path.extname(filePath).toLowerCase())
    ? zlib.constants.BROTLI_MODE_TEXT
    : zlib.constants.BROTLI_MODE_GENERIC
}

async function writeSidecar(filePath, suffix, compressedBuffer, originalSize) {
  const sidecarPath = `${filePath}${suffix}`

  if (compressedBuffer.length >= originalSize) {
    await rm(sidecarPath, {force: true})
    return 0
  }

  await writeFile(sidecarPath, compressedBuffer)
  return compressedBuffer.length
}

const summary = {
  files: 0,
  originalBytes: 0,
  gzipBytes: 0,
  brotliBytes: 0,
  zstdBytes: 0,
}

async function processFile(filePath) {
  if (!isCompressible(filePath) || filePath.endsWith('.br') || filePath.endsWith('.gz') || filePath.endsWith('.zst')) {
    return
  }

  const source = await readFile(filePath)
  if (source.length < minimumBytes) {
    return
  }

  summary.files += 1
  summary.originalBytes += source.length
  const compressionTasks = [
    gzipAsync(source, {level: 9}),
    brotliCompressAsync(source, {
      params: {
        [zlib.constants.BROTLI_PARAM_MODE]: brotliModeFor(filePath),
        [zlib.constants.BROTLI_PARAM_QUALITY]: 10,
      },
    }),
  ]

  if (zstdCompressAsync) {
    compressionTasks.push(zstdCompressAsync(source, {level: 15}))
  }

  const compressedBuffers = await Promise.all(compressionTasks)
  const [gzipBuffer, brotliBuffer, zstdBuffer] = compressedBuffers

  summary.gzipBytes += await writeSidecar(filePath, '.gz', gzipBuffer, source.length)
  summary.brotliBytes += await writeSidecar(filePath, '.br', brotliBuffer, source.length)
  if (zstdCompressAsync && zstdBuffer) {
    summary.zstdBytes += await writeSidecar(filePath, '.zst', zstdBuffer, source.length)
  } else {
    await rm(`${filePath}.zst`, {force: true})
  }
}

const inFlight = new Set()

for await (const filePath of walk(distDir)) {
  if (inFlight.size >= concurrency) {
    await Promise.race(inFlight)
  }

  const task = processFile(filePath).finally(() => {
    inFlight.delete(task)
  })
  inFlight.add(task)
}

await Promise.all(inFlight)

console.log(`Precompressed ${summary.files} frontend assets in ${distDir}`)
console.log(`- original bytes: ${summary.originalBytes}`)
console.log(`- gzip bytes: ${summary.gzipBytes}`)
console.log(`- brotli bytes: ${summary.brotliBytes}`)
if (zstdCompressAsync) {
  console.log(`- zstd bytes: ${summary.zstdBytes}`)
} else {
  console.log('- zstd bytes: skipped (node:zlib zstdCompress unavailable)')
}