import { useCallback } from 'react'
import en from '@/i18n/en.json'
import hi from '@/i18n/hi.json'
import mr from '@/i18n/mr.json'
import { useLanguageStore, type Language } from '@/i18n/store'

type Flat<T, P extends string = ''> = {
  [K in keyof T & string]: T[K] extends string ? `${P}${K}` : Flat<T[K], `${P}${K}.`>
}[keyof T & string]

export type TranslationKey = Flat<typeof en> | (string & {})
export type { Language }

type Tree = { [key: string]: string | Tree }

function flatten(tree: Tree, prefix = '', out: Record<string, string> = {}): Record<string, string> {
  for (const [key, value] of Object.entries(tree)) {
    if (typeof value === 'string') out[prefix + key] = value
    else flatten(value, `${prefix}${key}.`, out)
  }
  return out
}

const resources: Record<Language, Record<string, string>> = {
  en: flatten(en as Tree),
  hi: flatten(hi as Tree),
  mr: flatten(mr as Tree),
}

export function translate(
  language: Language,
  key: TranslationKey,
  vars?: Record<string, string | number>,
): string {
  const template = resources[language][key] ?? resources.en[key] ?? key
  if (!vars) return template
  return template.replace(/\{(\w+)\}/g, (match, name) =>
    Object.prototype.hasOwnProperty.call(vars, name) ? String(vars[name]) : match,
  )
}

export function useLanguage() {
  const language = useLanguageStore((s) => s.language)
  const setLanguage = useLanguageStore((s) => s.setLanguage)

  const t = useCallback(
    (key: TranslationKey, vars?: Record<string, string | number>) =>
      translate(language, key, vars),
    [language],
  )

  return { language, setLanguage, t }
}

