import { createReadStream, createWriteStream, promises as fs } from 'fs';
import { createGzip, createBrotliCompress, constants } from 'zlib';
import { pipeline } from 'stream/promises';
import { join, extname, relative } from 'path';

const TARGET_DIR = './dist/grimmory';
const EXTENSIONS = new Set(['.js', '.css', '.html', '.svg', '.json', '.wasm', '.ico']);

async function getFiles(dir) {
  const dirents = await fs.readdir(dir, { withFileTypes: true });
  const files = await Promise.all(dirents.map((dirent) => {
    const res = join(dir, dirent.name);
    return dirent.isDirectory() ? getFiles(res) : res;
  }));
  return files.flat();
}

async function compressFile(filePath) {
  const ext = extname(filePath);
  if (!EXTENSIONS.has(ext)) return null;

  const stats = await fs.stat(filePath);
  if (stats.size < 1024) return null; // Don't compress very small files

  const gzipPath = `${filePath}.gz`;
  const brPath = `${filePath}.br`;

  await Promise.all([
    pipeline(
      createReadStream(filePath),
      createGzip({ level: 9 }),
      createWriteStream(gzipPath)
    ),
    pipeline(
      createReadStream(filePath),
      createBrotliCompress({
        params: {
          [constants.BROTLI_PARAM_QUALITY]: 11,
        },
      }),
      createWriteStream(brPath)
    )
  ]);

  const [gzipStats, brStats] = await Promise.all([
    fs.stat(gzipPath),
    fs.stat(brPath)
  ]);

  return {
    path: filePath,
    original: stats.size,
    gzip: gzipStats.size,
    br: brStats.size
  };
}

async function run() {
  console.log(`Starting compression in ${TARGET_DIR}...`);
  try {
    const allFiles = await getFiles(TARGET_DIR);
    const results = (await Promise.all(allFiles.map(compressFile))).filter(Boolean);

    if (results.length === 0) {
      console.log('No files found to compress.');
      return;
    }

    console.log('\n Compression Results:');
    console.table(results.map(r => ({
      File: relative(TARGET_DIR, r.path),
      Original: `${(r.original / 1024).toFixed(2)} KB`,
      Gzip: `${(r.gzip / 1024).toFixed(2)} KB (${(100 * r.gzip / r.original).toFixed(1)}%)`,
      Brotli: `${(r.br / 1024).toFixed(2)} KB (${(100 * r.br / r.original).toFixed(1)}%)`
    })));

    const totalOrig = results.reduce((a, b) => a + b.original, 0);
    const totalGzip = results.reduce((a, b) => a + b.gzip, 0);
    const totalBr = results.reduce((a, b) => a + b.br, 0);

    console.log(`\n Total reduction:`);
    console.log(`   Gzip:   ${(totalOrig / 1024).toFixed(2)} KB -> ${(totalGzip / 1024).toFixed(2)} KB (${(100 * totalGzip / totalOrig).toFixed(1)}%)`);
    console.log(`   Brotli: ${(totalOrig / 1024).toFixed(2)} KB -> ${(totalBr / 1024).toFixed(2)} KB (${(100 * totalBr / totalOrig).toFixed(1)}%)`);
  } catch (err) {
    console.error('Compression failed:', err);
    process.exit(1);
  }
}

run();
