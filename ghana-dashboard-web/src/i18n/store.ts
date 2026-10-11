import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type Language = 'en' | 'hi' | 'mr'

interface LanguageState {
  language: Language
  setLanguage: (language: Language) => void
}

export const useLanguageStore = create<LanguageState>()(
  persist(
    (set) => ({
      language: 'en',
      setLanguage: (language) => set({ language }),
    }),
    { name: 'lakadi.language' },
  ),
)

// Keep the document element honest: dev tools, assistive tech and fonts all read it.
function syncHtmlLanguage(language: Language | undefined) {
  if (typeof document === 'undefined') return
  const lang = language ?? useLanguageStore.getState().language
  document.documentElement.lang = lang
}

syncHtmlLanguage(useLanguageStore.getState().language)
useLanguageStore.subscribe((state) => syncHtmlLanguage(state.language))

