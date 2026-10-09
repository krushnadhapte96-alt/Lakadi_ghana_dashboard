import type { SVGProps } from 'react'

/**
 * A wooden lakdi ghani press: a swinging wooden beam grinding mustard seeds in a base.
 * Replacement for the old country illustration on the auth screens.
 */
export function WoodenPress(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 64 64" fill="none" aria-hidden="true" {...props}>
      <path d="M10 54h44" stroke="#7C5C3E" strokeWidth="3" strokeLinecap="round" />
      <path d="M16 54V34" stroke="#7C5C3E" strokeWidth="3" strokeLinecap="round" />
      <path
        d="M16 34 48 22c1.4-.6 3 .4 3 1.9v.2c0 1.3-1.3 2.3-2.7 1.9L16 34z"
        fill="#A66A2E"
        stroke="#7C5C3E"
        strokeWidth="2"
        strokeLinejoin="round"
      />
      <path
        d="M46 26.8c3.4 3 5.2 7.6 4.2 12.5-.9 4.5-4.5 7.7-8.9 7.7h-2.5v-8.3h7.2z"
        fill="#E9C46A"
        stroke="#B88A2C"
        strokeWidth="2"
        strokeLinejoin="round"
      />
      <path
        d="M41.2 47h11.4l-1.4 7H42.6z"
        fill="#F4A261"
        stroke="#B4641E"
        strokeWidth="2"
        strokeLinejoin="round"
      />
      <path d="M32 52v2M24 54v2M40 54v2" stroke="#5C7C3E" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}


