// Builds app/src/main/assets/i18n.json = every dictionary key the Kotlin code can ask for, in [en, hi, mr].
// Source order: tools/i18n/extra.json (this app's own words, wins) → original app dictionary (tools/i18n/original-L.js).
// Usage: node tools/i18n/build.mjs [--check]   (--check fails if any key is missing; prints the list)
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../..');
const L = require('./original-L.js');
const extraPath = path.join(root, 'tools/i18n/extra.json');
const extra = fs.existsSync(extraPath) ? JSON.parse(fs.readFileSync(extraPath, 'utf8')) : {};
const src = path.join(root, 'app/src/main/java');
const files = [];
(function walk(d) { for (const f of fs.readdirSync(d)) { const p = path.join(d, f); fs.statSync(p).isDirectory() ? walk(p) : p.endsWith('.kt') && files.push(p); } })(src);
const code = Object.fromEntries(files.map(f => [path.basename(f), fs.readFileSync(f, 'utf8')]));
const all = Object.values(code).join('\n');
const keys = new Set();
const add = k => k && keys.add(k);
const grab = (re, text = all, g = 1) => { for (const m of text.matchAll(re)) add(m[g]); };
grab(/\b(?:t|tr)\(\s*"([a-z][a-z0-9_]*)"/g);
grab(/I18n\.t\([A-Za-z.]+,\s*"([a-z][a-z0-9_]*)"/g);
grab(/"(err_[a-z0-9_]+)"/g);
grab(/t\(if \(.*?\)\s*"([a-z][a-z0-9_]*)"/g);
grab(/t\(if \(.*?\)\s*"[a-z_]+"\s*else\s*"([a-z][a-z0-9_]*)"/g);
grab(/F\.(?:Text|Choice|Checks|Person|Lines|Sign)\("[^"]*",\s*"([a-z][a-z0-9_]*)"/g);
grab(/F\.(?:Photos|Gps)\("([a-z][a-z0-9_]*)"/g);
grab(/ActionSpec\("[A-Z_]+",\s*"([a-z][a-z0-9_]*)"/g);
grab(/"((?:saved|gps|bar|lvl|theme)_[a-z0-9_]+)"/g);
grab(/Triple\(Screen\.\w+,\s*"[^"]*",\s*"([a-z_]+)"\)/g);
// dynamic families from the domain constants
const lit = (re, text) => [...text.matchAll(re)].map(m => m[1]);
const model = code['Model.kt'], tasks = code['Tasks.kt'], rules = code['Rules.kt'], actions = code['Actions.kt'], cfg = code['Config.kt'], pricing = code['Pricing.kt'];
lit(/\("([a-z_0-9]+)", -?\d+, "/g, model).forEach(s => add('st_' + s));
lit(/[A-Z]+\("([a-z]+)", "[^"]+"\)[,;]/g, model.split('enum class Phase')[0]).forEach(r => add('r_' + r));
lit(/\w+\("([a-z]+)", "[^"]+"\)/g, model.split('enum class Phase')[1].split('enum class Stage')[0]).forEach(p => add('ph_' + p));
lit(/n\("([a-z_]+)", "/g, tasks).forEach(k => add('t_' + k));
lit(/"([A-Z_]+)" to Rule\(/g, rules).concat(['ORG_CREATED']).forEach(k => add('ev_' + k));
['token', 'arrival', 'final'].forEach(k => { add('pay_' + k); add('pw_' + k); });
const listOf = name => lit(/"([a-z_0-9]+)"/g, model.split('val ' + name)[1].split('\n')[0]);
listOf('RETURN_REASONS').forEach(k => add('rr_' + k));
listOf('LOST_REASONS').forEach(k => add('lr_' + k));
lit(/"([a-z]+)" to "/g, model.split('val SOP_STEPS')[1].split(')\n')[0]).forEach(k => { add('sop_' + k); add('sop_hint_' + k); });
lit(/"([a-z]+)" to "/g, model.split('val PREP_ITEMS')[1].split('\n')[0]).forEach(k => { add('prep_' + k); add('prep_hint_' + k); });
listOf('QC_SITE_CHECKS').forEach(k => add('qcs_' + k));
listOf('QC_FINAL_CHECKS').forEach(k => add('qcf_' + k));
for (const m of actions.matchAll(/opts\(c, "([a-z_]+)", ([^)]*)\)/g)) lit(/"([a-z_0-9]+)"/g, m[2]).forEach(v => add(m[1] + v));
['open', 'assigned', 'fixed', 'closed', 'reopened'].forEach(k => add('tk_' + k));
['seal_mismatch', 'seal_broken', 'damage'].forEach(k => add('inc_' + k));
['customer', 'tech', 'qc'].forEach(k => add('slot_' + k));
['over_limit', 'over_admin', 'below_floor'].forEach(k => add('why_' + k));
['width', 'depth', 'pit', 'head'].forEach(k => add('meas_' + k));
lit(/"([a-zA-Z]+)" to [0-9.]+\.\./g, cfg).concat(lit(/TEXT_KEYS = setOf\(([^)]*)\)/g, cfg).flatMap(x => lit(/"(\w+)"/g, x))).forEach(k => add('cfg_' + k));
lit(/"(m_[a-z]+)" to /g, pricing).forEach(add);
// drop pure prefixes captured by mistake
for (const k of [...keys]) if (k.endsWith('_')) keys.delete(k);

const out = {}, missing = [], fromOriginal = [];
const ph = s => [...s.matchAll(/\{(\w+)\}/g)].map(m => m[1]).sort().join(',');
for (const k of [...keys].sort()) {
  const e = extra[k] || L[k];
  if (!e) { missing.push(k); continue; }
  if (!extra[k]) fromOriginal.push(k);
  if (e.length !== 3 || e.some(x => !x)) { missing.push(k + ' (incomplete)'); continue; }
  if (new Set(e.map(ph)).size !== 1) { missing.push(k + ' (placeholders differ)'); continue; }
  out[k] = e;
}
fs.writeFileSync(path.join(root, 'app/src/main/assets/i18n.json'), JSON.stringify(out, null, 1) + '\n');
fs.writeFileSync(path.join(root, 'tools/i18n/keys-from-original.txt'), fromOriginal.map(k => `${k}\t${L[k].join(' | ')}`).join('\n') + '\n');
console.log(`keys used: ${keys.size} · written: ${Object.keys(out).length} · from original: ${fromOriginal.length} · own: ${Object.keys(out).length - fromOriginal.length} · missing: ${missing.length}`);
if (missing.length) { fs.writeFileSync(path.join(root, 'tools/i18n/missing.txt'), missing.join('\n') + '\n'); if (process.argv.includes('--check')) { console.log(missing.join('\n')); process.exit(1); } }
