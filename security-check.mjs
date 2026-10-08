import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { extname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const cwd = fileURLToPath(new URL('.', import.meta.url));
const git = (...args) => execFileSync('git', args, { cwd, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
git('rev-parse', '--show-toplevel');
const prefix = git('rev-parse', '--show-prefix').trim();
const split = (text) => text.split('\0').filter(Boolean);
const findings = new Set();
const sensitive = /(?:^|\/)(?:\.env(?:\..+)?|\.npmrc|\.netrc|credentials[^/]*\.json|[^/]*service-account[^/]*\.json)$|\.(?:env|pem|key|p12|pfx|jks|keystore|har|sql|sqlite|db)$/i;
const generated = /(?:^|\/)(?:node_modules|dist|dist-ssr|artifacts|test-results|customer-test-results|e2e-report|\.auth|coverage)(?:\/|$)|\.log$/i;
const textExtensions = new Set(['.js', '.jsx', '.ts', '.tsx', '.json', '.md', '.html', '.css', '.yml', '.yaml', '.txt', '.xml', '.properties', '.conf', '.svg', '.mjs', '.sh', '.ps1']);
const patterns = [
  ['private-key', /-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----/],
  ['github-token', /\b(?:gh[pousr]_[A-Za-z0-9_]{30,}|github_pat_[A-Za-z0-9_]{30,})\b/],
  ['aws-access-key', /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/],
  ['provider-secret', /\b(?:sk-(?:proj-|ant-)?[A-Za-z0-9_-]{20,}|AIza[A-Za-z0-9_-]{30,}|xox[baprs]-[A-Za-z0-9-]{20,})\b/],
  ['embedded-url-credentials', /https?:\/\/[^\s/:]+:[^\s/@]+@/],
  ['hardcoded-credential', /(?:password|api[_-]?key|client[_-]?secret|private[_-]?key)\s*[:=]\s*['"][^'"\r\n]{6,}['"]/i],
  ['jwt', /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/],
];
const report = (scope, path, category, line = '') => findings.add(`${scope}: ${path}${line ? ':' + line : ''} [${category}]`);
function inspect(scope, path, text) {
  text.split(/\r?\n/).forEach((line, index) => {
    for (const [name, pattern] of patterns) if (pattern.test(line)) report(scope, path, name, index + 1);
  });
}
function inspectPath(scope, path) {
  if (sensitive.test(path) && !path.endsWith('/.env.example') && path !== '.env.example') report(scope, path, 'sensitive-file');
  if (generated.test(path)) report(scope, path, 'generated-or-private-artifact');
}
function isText(path) {
  return textExtensions.has(extname(path)) || /(?:^|\/)(?:Dockerfile|\.env(?:\..+)?|\.npmrc|\.netrc)$/.test(path);
}
const tracked = split(git('ls-files', '-z'));
const candidates = new Set([...tracked, ...split(git('ls-files', '--others', '--exclude-standard', '-z'))]);
for (const localPath of candidates) {
  const path = prefix + localPath;
  inspectPath('working-tree', path);
  if (isText(path)) {
    try { inspect('working-tree', path, readFileSync(resolve(cwd, localPath), 'utf8')); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
}
// Inspect the frontend index even when a working-tree edit removed a secret.
for (const localPath of tracked) {
  const path = prefix + localPath;
  inspectPath('index', path);
  if (isText(path)) inspect('index', path, git('show', `:${path}`));
}
// Scan staged changes across the repo: a root-level commit can include backend files too.
for (const path of split(git('diff', '--cached', '--name-only', '--diff-filter=ACMR', '-z'))) {
  inspectPath('staged', path);
  if (isText(path)) inspect('staged', path, git('show', `:${path}`));
}
// Verify private files are excluded even if frontend is copied into its own repository.
for (const path of ['.env', '.env.production', '.env.local', '.env.production.local', 'artifacts/private.png', 'playwright/.auth/state.json']) {
  try { git('check-ignore', '--no-index', '-q', path); }
  catch { report('ignore-rules', prefix + path, 'not-ignored'); }
}
let envText = '';
try { envText = readFileSync(resolve(cwd, '.env'), 'utf8'); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
for (const line of envText.split(/\r?\n/)) {
  const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=/);
  if (match && match[1] !== 'VITE_API_BASE_URL') report('local-env', prefix + '.env', 'unreviewed-variable:' + match[1]);
}
inspect('local-env', prefix + '.env', envText);

if (process.argv.includes('--history')) {
  // Check every reachable frontend text blob, not only HEAD; never print blob contents.
  const scope = prefix || '.';
  const objects = git('rev-list', '--objects', '--all', '--', scope).trim().split('\n');
  const checked = new Set();
  for (const entry of objects) {
    const separator = entry.indexOf(' ');
    if (separator < 0) continue;
    const id = entry.slice(0, separator);
    const path = entry.slice(separator + 1);
    if (checked.has(id) || !isText(path)) continue;
    checked.add(id);
    if (git('cat-file', '-t', id).trim() !== 'blob') continue;
    inspectPath('history', path);
    inspect('history', path, git('cat-file', 'blob', id));
  }
}
if (findings.size) {
  console.error('Push blocked: review these findings; no secret values are printed.');
  for (const finding of findings) console.error(finding);
  process.exitCode = 1;
} else {
  console.log(`No findings in frontend working tree or staged files${process.argv.includes('--history') ? ' and reachable frontend text history' : ''}.`);
  console.log('Pattern checks are not a guarantee. Review staged changes; enable GitHub secret scanning/push protection.');
}
