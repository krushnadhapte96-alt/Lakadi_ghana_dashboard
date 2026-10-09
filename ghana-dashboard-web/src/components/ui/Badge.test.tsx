import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Badge } from '@/components/ui/Badge'

describe('Badge', () => {
  it('renders children with the given variant class', () => {
    render(<Badge variant="danger">Out of stock</Badge>)
    const el = screen.getByText('Out of stock')
    expect(el).toBeInTheDocument()
    expect(el.className).toContain('crimson')
  })

  it('renders a dot when asked', () => {
    const { container } = render(<Badge dot>3 left</Badge>)
    expect(container.querySelector('span span')).not.toBeNull()
  })
})

