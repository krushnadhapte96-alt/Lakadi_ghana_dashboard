import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { useLanguageStore } from '@/i18n/store'

export function LanguageProvider({ children }: { children: ReactNode }) {
  const language = useLanguageStore((s) => s.language)

  useEffect(() => {
    document.documentElement.lang = language
    document.documentElement.classList.toggle('hi', language === 'hi')
  }, [language])

  return <>{children}</>
}
