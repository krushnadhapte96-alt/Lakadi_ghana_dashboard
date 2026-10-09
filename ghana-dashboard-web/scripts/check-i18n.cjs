const fs = require('fs')
const path = require('path')

const en = JSON.parse(fs.readFileSync('./src/i18n/en.json', 'utf8'))
const hi = JSON.parse(fs.readFileSync('./src/i18n/hi.json', 'utf8'))

const files = []
const walk = (dir) => {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(p)
    else if (/\.(ts|tsx)$/.test(entry.name)) files.push(p)
  }
}
walk('src')
const src = files.map((f) => fs.readFileSync(f, 'utf8')).join('\n')

const used = (key) => src.includes("'" + key + "'") || src.includes('`' + key + '`')
const unused = Object.keys(en).filter((k) => !used(k))
const missingInHi = Object.keys(en).filter((k) => !(k in hi))
const extraInHi = Object.keys(hi).filter((k) => !(k in en))

console.log('en keys:', Object.keys(en).length)
console.log('hi keys:', Object.keys(hi).length)
console.log('unused in src:', unused.length)
for (const k of unused) console.log('  ', k)
console.log('missing in hi:', missingInHi.length, missingInHi.join(', '))
console.log('extra in hi:', extraInHi.length, extraInHi.join(', '))