import { Component, HostListener, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <header class="bg-white border-b border-gray-100 sticky top-0 z-50">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <!-- Logo -->
          <div class="flex items-center">
            <a routerLink="/" class="flex items-center space-x-2.5 group">
              <div class="w-9 h-9 bg-blue-600 rounded-xl flex items-center justify-center group-hover:bg-blue-700 transition-colors">
                <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"></path>
                </svg>
              </div>
              <span class="text-lg font-bold text-gray-900 hidden sm:block">CodeReviewer</span>
            </a>
          </div>

          <!-- Desktop Navigation -->
          @if (authService.isLoggedIn()) {
            <nav class="hidden md:flex items-center space-x-1">
              <a
                routerLink="/review"
                routerLinkActive="nav-link-active"
                [routerLinkActiveOptions]="{ exact: true }"
                class="nav-link">
                <svg class="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"></path>
                </svg>
                Review
              </a>
              <a
                routerLink="/files"
                routerLinkActive="nav-link-active"
                class="nav-link">
                <svg class="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"></path>
                </svg>
                Files
              </a>
              <a
                routerLink="/github"
                routerLinkActive="nav-link-active"
                class="nav-link">
                <svg class="w-4 h-4 mr-1.5" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                </svg>
                GitHub
              </a>
              <a
                routerLink="/history"
                routerLinkActive="nav-link-active"
                class="nav-link">
                <svg class="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                History
              </a>
            </nav>
          }

          <!-- Right side: Auth actions -->
          <div class="flex items-center">
            @if (authService.isLoggedIn()) {
              <!-- User Avatar Dropdown (desktop) -->
              <div class="hidden md:block relative">
                <button
                  (click)="toggleDropdown()"
                  class="flex items-center space-x-2 px-3 py-1.5 rounded-lg hover:bg-gray-50 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-1">
                  <!-- Avatar -->
                  @if (authService.currentUser()?.avatarUrl) {
                    <img
                      [src]="authService.currentUser()!.avatarUrl"
                      [alt]="authService.currentUser()?.name"
                      class="w-8 h-8 rounded-full object-cover border border-gray-200">
                  } @else {
                    <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-sm font-semibold">
                      {{ getUserInitials() }}
                    </div>
                  }
                  <span class="text-sm font-medium text-gray-700 max-w-[120px] truncate">
                    {{ authService.currentUser()?.name || authService.currentUser()?.email }}
                  </span>
                  <svg
                    class="w-4 h-4 text-gray-400 transition-transform duration-200"
                    [class.rotate-180]="isDropdownOpen()"
                    fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"></path>
                  </svg>
                </button>

                <!-- Dropdown Menu -->
                @if (isDropdownOpen()) {
                  <div class="absolute right-0 mt-2 w-56 bg-white rounded-lg shadow-lg border border-gray-200 py-1 z-50 animate-fade-in">
                    <div class="px-4 py-3 border-b border-gray-100">
                      <p class="text-sm font-medium text-gray-900 truncate">{{ authService.currentUser()?.name }}</p>
                      <p class="text-xs text-gray-500 truncate">{{ authService.currentUser()?.email }}</p>
                    </div>
                    <a
                      routerLink="/files"
                      (click)="closeDropdown()"
                      class="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                      <svg class="w-4 h-4 mr-3 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"></path>
                      </svg>
                      My Files
                    </a>
                    <a
                      routerLink="/history"
                      (click)="closeDropdown()"
                      class="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                      <svg class="w-4 h-4 mr-3 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                      </svg>
                      Review History
                    </a>
                    <div class="border-t border-gray-100 my-1"></div>
                    <button
                      (click)="logout()"
                      class="w-full flex items-center px-4 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors">
                      <svg class="w-4 h-4 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path>
                      </svg>
                      Sign out
                    </button>
                  </div>
                }
              </div>

              <!-- Mobile Menu Button -->
              <button
                (click)="toggleMobileMenu()"
                class="md:hidden p-2 rounded-lg text-gray-500 hover:text-gray-700 hover:bg-gray-100 transition-colors ml-2">
                @if (isMobileMenuOpen()) {
                  <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                  </svg>
                } @else {
                  <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16"></path>
                  </svg>
                }
              </button>
            } @else {
              <!-- Not logged in -->
              <a
                routerLink="/login"
                class="px-4 py-2 text-sm font-medium text-gray-600 hover:text-gray-900 hover:bg-gray-50 rounded-lg transition-colors hidden sm:block">
                Sign in
              </a>
              <a
                routerLink="/signup"
                class="btn btn-primary text-sm ml-2 hidden sm:block">
                Sign up
              </a>
              <!-- Mobile: show hamburger for login/signup -->
              <button
                (click)="toggleMobileMenu()"
                class="sm:hidden p-2 rounded-lg text-gray-500 hover:text-gray-700 hover:bg-gray-100 transition-colors">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16"></path>
                </svg>
              </button>
            }
          </div>
        </div>
      </div>

      <!-- Mobile Menu -->
      @if (isMobileMenuOpen()) {
        <div class="md:hidden border-t border-gray-100 bg-white animate-slide-down">
          @if (authService.isLoggedIn()) {
            <!-- User Info -->
            <div class="px-4 py-4 border-b border-gray-100 flex items-center gap-3">
              @if (authService.currentUser()?.avatarUrl) {
                <img
                  [src]="authService.currentUser()!.avatarUrl"
                  [alt]="authService.currentUser()?.name"
                  class="w-10 h-10 rounded-full object-cover border border-gray-200">
              } @else {
                <div class="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-sm font-semibold">
                  {{ getUserInitials() }}
                </div>
              }
              <div>
                <p class="text-sm font-medium text-gray-900">{{ authService.currentUser()?.name }}</p>
                <p class="text-xs text-gray-500">{{ authService.currentUser()?.email }}</p>
              </div>
            </div>

            <!-- Nav Links -->
            <nav class="py-2">
              <a routerLink="/review" (click)="closeMobileMenu()" routerLinkActive="bg-blue-50 text-blue-600" [routerLinkActiveOptions]="{ exact: true }"
                class="flex items-center px-4 py-3 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors">
                <svg class="w-5 h-5 mr-3 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"></path>
                </svg>
                Review
              </a>
              <a routerLink="/files" (click)="closeMobileMenu()" routerLinkActive="bg-blue-50 text-blue-600"
                class="flex items-center px-4 py-3 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors">
                <svg class="w-5 h-5 mr-3 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"></path>
                </svg>
                Files
              </a>
              <a routerLink="/github" (click)="closeMobileMenu()" routerLinkActive="bg-blue-50 text-blue-600"
                class="flex items-center px-4 py-3 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors">
                <svg class="w-5 h-5 mr-3 text-gray-400" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                </svg>
                GitHub
              </a>
              <a routerLink="/history" (click)="closeMobileMenu()" routerLinkActive="bg-blue-50 text-blue-600"
                class="flex items-center px-4 py-3 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors">
                <svg class="w-5 h-5 mr-3 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                History
              </a>
            </nav>

            <!-- Sign Out -->
            <div class="border-t border-gray-100 py-2">
              <button
                (click)="logout()"
                class="w-full flex items-center px-4 py-3 text-sm font-medium text-red-600 hover:bg-red-50 transition-colors">
                <svg class="w-5 h-5 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path>
                </svg>
                Sign out
              </button>
            </div>
          } @else {
            <!-- Mobile: Login / Signup -->
            <div class="py-4 px-4 space-y-2">
              <a routerLink="/login" (click)="closeMobileMenu()"
                class="block w-full text-center px-4 py-2.5 text-sm font-medium text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors">
                Sign in
              </a>
              <a routerLink="/signup" (click)="closeMobileMenu()"
                class="block w-full text-center px-4 py-2.5 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors">
                Sign up
              </a>
            </div>
          }
        </div>
      }
    </header>
  `,
  styles: [`
    .nav-link {
      @apply px-3 py-2 text-sm font-medium text-gray-500 hover:text-gray-900 hover:bg-gray-50 rounded-lg transition-colors flex items-center;
    }
    .nav-link-active {
      @apply bg-blue-50 text-blue-600 hover:text-blue-700 hover:bg-blue-50;
    }
    .animate-fade-in {
      animation: fadeIn 0.15s ease-out;
    }
    .animate-slide-down {
      animation: slideDown 0.2s ease-out;
    }
    @keyframes fadeIn {
      from { opacity: 0; transform: scale(0.95); }
      to { opacity: 1; transform: scale(1); }
    }
    @keyframes slideDown {
      from { opacity: 0; transform: translateY(-8px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `]
})
export class HeaderComponent {
  isDropdownOpen = signal(false);
  isMobileMenuOpen = signal(false);

  constructor(
    public authService: AuthService,
    private router: Router
  ) {}

  toggleDropdown() {
    this.isDropdownOpen.set(!this.isDropdownOpen());
    this.isMobileMenuOpen.set(false);
  }

  closeDropdown() {
    this.isDropdownOpen.set(false);
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen.set(!this.isMobileMenuOpen());
    this.isDropdownOpen.set(false);
  }

  closeMobileMenu() {
    this.isMobileMenuOpen.set(false);
  }

  getUserInitials(): string {
    const user = this.authService.currentUser();
    if (!user) return '?';
    if (user.name) {
      const parts = user.name.split(' ').filter(Boolean);
      if (parts.length >= 2) {
        return (parts[0][0] + parts[1][0]).toUpperCase();
      }
      return parts[0]?.substring(0, 2).toUpperCase() || '?';
    }
    return user.email?.substring(0, 2).toUpperCase() || '?';
  }

  logout() {
    this.closeDropdown();
    this.closeMobileMenu();
    this.authService.logout();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (!target.closest('.relative')) {
      this.closeDropdown();
    }
  }
}
