import fs from 'node:fs'
import path from 'node:path'

const i18nDir = path.join(import.meta.dirname, '..', 'src', 'i18n')

const files = {
  en: path.join(i18nDir, 'en.json'),
  hi: path.join(i18nDir, 'hi.json'),
  mr: path.join(i18nDir, 'mr.json'),
}

const MOJIBAKE = /à¤|â€|Ã/
const EMPTY = (v) => typeof v === 'string' && v.trim() === ''

function flattenKeys(obj, prefix = '', out = {}) {
  for (const [k, v] of Object.entries(obj)) {
    if (v && typeof v === 'object') flattenKeys(v, `${prefix}${k}.`, out)
    else out[`${prefix}${k}`] = v
  }
  return out
}

const bundle = {}
let failed = false

for (const [lang, file] of Object.entries(files)) {
  let parsed
  try {
    parsed = JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''))
  } catch (e) {
    console.error(`[${lang}] invalid JSON: ${e.message}`)
    failed = true
    continue
  }
  bundle[lang] = flattenKeys(parsed)
  for (const [k, v] of Object.entries(bundle[lang])) {
    if (EMPTY(v)) {
      console.error(`[${lang}] empty value at "${k}"`)
      failed = true
    }
    if (typeof v === 'string' && MOJIBAKE.test(v)) {
      console.error(`[${lang}] mojibake detected at "${k}": ${v.slice(0, 50)}`)
      failed = true
    }
  }
}

const enKeys = Object.keys(bundle.en || {}).sort()
for (const lang of ['hi', 'mr']) {
  const keys = Object.keys(bundle[lang] || {})
  const missing = enKeys.filter((k) => !keys.includes(k))
  const extra = keys.filter((k) => !enKeys.includes(k))
  if (missing.length) {
    console.error(`[${lang}] missing ${missing.length} keys: ${missing.slice(0, 10).join(', ')}${missing.length > 10 ? '…' : ''}`)
    failed = true
  }
  if (extra.length) {
    console.error(`[${lang}] extra ${extra.length} keys: ${extra.slice(0, 10).join(', ')}${extra.length > 10 ? '…' : ''}`)
    failed = true
  }
}

if (failed) {
  console.error('check:i18n FAILED')
  process.exit(1)
}
console.log('check:i18n OK - keys match, no mojibake, no empty values')
