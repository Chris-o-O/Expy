// Copies the web app (the same files GitHub Pages serves) into www/, the folder Capacitor packs into the APK,
// and adds Capacitor's browser runtime to that copy only (the PWA never loads it).
import { cpSync, mkdirSync, rmSync, readFileSync, writeFileSync } from 'node:fs';
rmSync('www', { recursive: true, force: true });
mkdirSync('www');
for (const f of ['index.html', 'sw.js', 'manifest.webmanifest', 'fonts', 'icons']) cpSync(f, `www/${f}`, { recursive: true });
cpSync('node_modules/@capacitor/core/dist/capacitor.js', 'www/capacitor.js');
const html = readFileSync('www/index.html', 'utf8');
if (!html.includes('<head>')) throw new Error('index.html: <head> not found');
writeFileSync('www/index.html', html.replace('<head>', '<head>\n<script src="capacitor.js"></script>'));
console.log('www/ ready');
