import { useState } from 'react'
import type { ReactNode } from 'react'
import { Sidebar } from '@/components/layout/Sidebar'
import { Header } from '@/components/layout/Header'
import { BottomNav } from '@/components/layout/BottomNav'
import { Drawer } from '@/components/layout/Drawer'
import { AuroraBackground } from '@/components/layout/AuroraBackground'
import { cn } from '@/utils/cn'

export interface AppLayoutProps {
  children: ReactNode
}

export function AppLayout({ children }: AppLayoutProps) {
  const [collapsed, setCollapsed] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)

  const toggleSidebar = () => setCollapsed(!collapsed)
  // The sidebar only exists on desktop; below `lg` the menu button opens the drawer instead.
  const handleMenuClick = () =>
    window.matchMedia('(min-width: 1024px)').matches ? toggleSidebar() : setDrawerOpen(true)
  const openDrawer = () => setDrawerOpen(true)
  const closeDrawer = () => setDrawerOpen(false)

  return (
    <div className="relative min-h-screen">
      <AuroraBackground />

      <Sidebar collapsed={collapsed} onToggle={toggleSidebar} />
      <Header onMenuClick={handleMenuClick} collapsed={collapsed} />
      <BottomNav onMoreClick={openDrawer} />
      <Drawer open={drawerOpen} onClose={closeDrawer} />

      <main
        className={cn(
          'pt-16 pb-20 transition-all duration-300 lg:pb-0',
          collapsed ? 'lg:pl-0' : 'lg:pl-64',
        )}
      >
        <div className="p-4 md:p-6 lg:p-8">{children}</div>
      </main>
    </div>
  )
}
