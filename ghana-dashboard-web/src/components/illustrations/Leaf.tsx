import type { SVGProps } from 'react'

export function Leaf(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 64 64" fill="none" aria-hidden="true" {...props}>
      <path
        d="M12 52C16 36 26 24 50 8C46 30 40 44 24 48C19 49 15 50 12 52Z"
        fill="var(--color-brand-500)"
      />
      <path
        d="M13 51C18 37 28 24 48 9"
        stroke="var(--color-brand-800)"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <path
        d="M20 44C25 39 29 34 31 29M28 36C32 31 36 26 38 21M36 27C39 23 42 19 44 14"
        stroke="var(--color-brand-700)"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
    </svg>
  )
}
