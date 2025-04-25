import {Component} from '@angular/core';
import {AuthService} from '../../service/auth.service';
import {Router} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {NgIf} from '@angular/common';
import {Card} from 'primeng/card';
import {Password} from 'primeng/password';
import {Button} from 'primeng/button';
import {Message} from 'primeng/message';
import {PrimeTemplate} from 'primeng/api';
import {InputText} from 'primeng/inputtext';

@Component({
  selector: 'app-login',
  imports: [
    FormsModule,
    NgIf,
    Card,
    Password,
    Button,
    Message,
    PrimeTemplate,
    InputText
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  username = '';
  password = '';
  errorMessage = '';

  constructor(private authService: AuthService, private router: Router) {
    this.authService.remoteLogin().subscribe({
      next: () => {
        this.router.navigate(['/dashboard']);
      },
    });
  }

  login(): void {
    this.authService.login({username: this.username, password: this.password}).subscribe({
      next: (response) => {
        if (response.isDefaultPassword === 'true') {
          this.router.navigate(['/change-password']);
        } else {
          this.router.navigate(['/dashboard']);
        }
      },
      error: (error) => {
        if (error.status === 0) {
          this.errorMessage = 'Cannot connect to the server. Please check your connection and try again.';
        } else {
          this.errorMessage = error?.error?.message || 'An unexpected error occurred. Please try again.';
        }
      }
    });
  }
}
