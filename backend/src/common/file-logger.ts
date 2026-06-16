import * as fs from 'fs';
import * as path from 'path';

const LOG_DIR = path.join(process.cwd(), 'logs');

if (!fs.existsSync(LOG_DIR)) fs.mkdirSync(LOG_DIR, { recursive: true });

// New timestamped log file per server run
const SESSION_TS = new Date().toISOString().replace(/:/g, '-').replace(/\..+/, '');
const LOG_FILE = path.join(LOG_DIR, `app-${SESSION_TS}.log`);

function write(level: string, context: string, message: string, data?: any) {
  const ts = new Date().toISOString();
  const line = `[${ts}] [${level}] [${context}] ${message}`;
  const extra = data !== undefined
    ? '\n' + JSON.stringify(data, null, 2)
    : '';
  const entry = line + extra + '\n';
  process.stdout.write(entry);
  fs.appendFileSync(LOG_FILE, entry);
}

export const fileLog = {
  info:  (ctx: string, msg: string, data?: any) => write('INFO ', ctx, msg, data),
  error: (ctx: string, msg: string, data?: any) => write('ERROR', ctx, msg, data),
  warn:  (ctx: string, msg: string, data?: any) => write('WARN ', ctx, msg, data),
};
