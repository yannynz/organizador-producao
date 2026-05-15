import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';

const root = process.cwd();
const port = Number(process.env.PORT || 80);

const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.gif': 'image/gif',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.jpeg': 'image/jpeg',
  '.jpg': 'image/jpeg',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain; charset=utf-8',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
};

function resolvePath(requestUrl) {
  const pathname = decodeURIComponent((requestUrl || '/').split('?')[0]);
  if (pathname.includes('..')) {
    return null;
  }
  return path.join(root, pathname === '/' ? 'index.html' : pathname);
}

async function fileOrIndex(filePath) {
  try {
    const info = await stat(filePath);
    if (info.isFile()) {
      return filePath;
    }
  } catch {
    // SPA fallback below.
  }
  return path.join(root, 'index.html');
}

createServer(async (req, res) => {
  const requestedPath = resolvePath(req.url);
  if (!requestedPath) {
    res.writeHead(400);
    res.end('bad request');
    return;
  }

  const filePath = await fileOrIndex(requestedPath);

  try {
    const body = await readFile(filePath);
    const extension = path.extname(filePath);
    const isIndex = path.basename(filePath) === 'index.html';
    res.writeHead(200, {
      'Content-Type': contentTypes[extension] || 'application/octet-stream',
      'Cache-Control': isIndex ? 'no-store' : 'public, max-age=31536000, immutable',
    });
    res.end(body);
  } catch {
    res.writeHead(404);
    res.end('not found');
  }
}).listen(port, '0.0.0.0', () => {
  console.log(`Frontend static server listening on ${port}`);
});
