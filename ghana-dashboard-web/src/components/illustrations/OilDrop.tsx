import type { SVGProps } from 'react'

export function OilDrop(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 64 64" fill="none" aria-hidden="true" {...props}>
      <defs>
        <linearGradient id="oil-drop-fill" x1="20" y1="8" x2="44" y2="60" gradientUnits="userSpaceOnUse">
          <stop stopColor="var(--color-gold-300)" />
          <stop offset="1" stopColor="var(--color-gold-600)" />
        </linearGradient>
      </defs>
      <path
        d="M32 5C41 18 49 27 49 38C49 48 41 56 32 56C23 56 15 48 15 38C15 27 23 18 32 5Z"
        fill="url(#oil-drop-fill)"
      />
      <ellipse
        cx="25"
        cy="32"
        rx="3.4"
        ry="5.6"
        fill="#fff"
        opacity="0.75"
        transform="rotate(-26 25 32)"
      />
      <path
        d="M18 42C18.5 47 21 51 24.5 54"
        stroke="#fff8e1"
        strokeOpacity="0.6"
        strokeWidth="3"
        strokeLinecap="round"
      />
    </svg>
  )
}
