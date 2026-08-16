import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { SignupComponent } from './auth/signup/signup.component';
import { ReviewComponent } from './features/review/review.component';
import { FilesComponent } from './features/files/files.component';
import { GithubComponent } from './features/github/github.component';
import { HistoryComponent } from './features/history/history.component';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'review', pathMatch: 'full' },
  { path: 'review', component: ReviewComponent, canActivate: [authGuard] },
  { path: 'login', component: LoginComponent },
  { path: 'signup', component: SignupComponent },
  { path: 'files', component: FilesComponent, canActivate: [authGuard] },
  { path: 'github', component: GithubComponent, canActivate: [authGuard] },
  { path: 'history', component: HistoryComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' }
];
