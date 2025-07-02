import { LoginCredentials, SignupCredentials, AuthResponse } from '@/types/auth';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

// Helper to decode JWT payload
function decodeJwtPayload(token: string): any {
  try {
    const payload = token.split('.')[1];
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decodeURIComponent(escape(decoded)));
  } catch (e) {
    return {};
  }
}

export const authService = {
  async login(credentials: LoginCredentials): Promise<AuthResponse> {
    console.log('Login attempt:', credentials);
    
    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
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
        
        // Decode JWT to get user info
        const claims = decodeJwtPayload(data.token);
        const user = {
          id: claims.sub || ('user_' + Date.now()),
          firstName: claims.firstName || 'User',
          lastName: claims.lastName || 'Name',
          email: claims.email || credentials.email
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
      const response = await fetch(`${API_BASE_URL}/api/auth/signup`, {
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
        
        // Decode JWT to get user info
        const claims = decodeJwtPayload(data.token);
        const user = {
          id: claims.sub || ('user_' + Date.now()),
          firstName: claims.firstName || credentials.firstName,
          lastName: claims.lastName || credentials.lastName,
          email: claims.email || credentials.email
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
