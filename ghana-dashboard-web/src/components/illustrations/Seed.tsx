import type { SVGProps } from 'react'

export function Seed(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 64 64" fill="none" aria-hidden="true" {...props}>
      <path
        d="M32 26C40 24 48 30 48 42C48 52 41 58 32 58C23 58 16 52 16 42C16 30 24 24 32 26Z"
        fill="var(--color-gold-400)"
      />
      <path
        d="M32 26C30 34 30 46 32 56"
        stroke="var(--color-gold-700)"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <path
        d="M32 26C32 21 32 16 32 12"
        stroke="var(--color-brand-700)"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <path
        d="M32 18C26 16 22 12 22 8C27 8 31 12 32 18Z"
        fill="var(--color-brand-500)"
      />
      <path
        d="M32 18C38 16 42 12 42 8C37 8 33 12 32 18Z"
        fill="var(--color-brand-600)"
      />
    </svg>
  )
}
