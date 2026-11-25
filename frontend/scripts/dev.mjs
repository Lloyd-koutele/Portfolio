// Starts both dev servers: the book on :5173 (which also forwards /admin) and the administration on :5174.
import { spawn } from 'node:child_process';

const apps = ['@portfolio/admin', '@portfolio/book'];
const children = apps.map((name) =>
  spawn('npm', ['run', 'dev', '-w', name], {
    stdio: 'inherit',
    shell: process.platform === 'win32',
  }),
);
const stop = () => children.forEach((child) => child.kill('SIGTERM'));
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
children.forEach((child) => child.on('exit', (code) => code && (stop(), process.exit(code))));
