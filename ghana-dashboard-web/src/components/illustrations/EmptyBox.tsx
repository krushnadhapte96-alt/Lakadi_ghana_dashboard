import type { SVGProps } from 'react'

export function EmptyBox(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 64 64" fill="none" aria-hidden="true" {...props}>
      <path
        d="M18 30L28 24H50L40 30Z"
        fill="var(--color-surface-3)"
        stroke="var(--color-ink-soft)"
        strokeWidth="2.5"
        strokeLinejoin="round"
      />
      <path
        d="M18 30V54H40V30"
        fill="var(--color-surface-2)"
        stroke="var(--color-ink-soft)"
        strokeWidth="2.5"
        strokeLinejoin="round"
      />
      <path
        d="M40 30L50 24V48L40 54Z"
        fill="var(--color-surface-3)"
        stroke="var(--color-ink-soft)"
        strokeWidth="2.5"
        strokeLinejoin="round"
      />
      <path
        d="M26 27L22 33M34 25L30 31"
        stroke="var(--color-ink-soft)"
        strokeWidth="2"
        strokeLinecap="round"
        strokeDasharray="3 3"
        opacity="0.7"
      />
    </svg>
  )
}
