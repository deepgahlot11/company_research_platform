
import { LoginCredentials, SignupCredentials, AuthResponse } from '@/types/auth';

export const authService = {
  async login(credentials: LoginCredentials): Promise<AuthResponse> {
    console.log('Login attempt:', credentials);
    
    try {
      const response = await fetch('http://localhost:8085/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(credentials),
      });

      const data = await response.json();

      if (response.ok && data.token) {
        // Store token in sessionStorage
        sessionStorage.setItem('authToken', data.token);
        
        // Create a user object from the token or credentials
        // In a real app, you might decode the JWT to get user info
        const user = {
          id: 'user_' + Date.now(),
          firstName: 'User',
          lastName: 'Name',
          email: credentials.email
        };

        return {
          success: true,
          user: user,
          token: data.token
        };
      } else {
        return {
          success: false,
          message: data.message || 'Login failed'
        };
      }
    } catch (error) {
      console.error('Login error:', error);
      return {
        success: false,
        message: 'Network error during login'
      };
    }
  },

  async signup(credentials: SignupCredentials): Promise<AuthResponse> {
    console.log('Signup attempt:', credentials);
    
    try {
      const response = await fetch('http://localhost:8085/api/auth/signup', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(credentials),
      });

      const data = await response.json();

      if (response.ok && data.token) {
        // Store token in sessionStorage
        sessionStorage.setItem('authToken', data.token);
        
        const user = {
          id: 'user_' + Date.now(),
          firstName: credentials.firstName,
          lastName: credentials.lastName,
          email: credentials.email
        };

        return {
          success: true,
          user: user,
          token: data.token
        };
      } else {
        return {
          success: false,
          message: data.message || 'Signup failed'
        };
      }
    } catch (error) {
      console.error('Signup error:', error);
      return {
        success: false,
        message: 'Network error during signup'
      };
    }
  },

  async logout(): Promise<void> {
    console.log('User logged out');
    // Remove token from sessionStorage
    sessionStorage.removeItem('authToken');
    sessionStorage.removeItem('user');
  },

  getToken(): string | null {
    return sessionStorage.getItem('authToken');
  },

  isAuthenticated(): boolean {
    return !!this.getToken();
  }
};
