export const environment = {
  production: true,
  apiUrl: (window as any)['env']?.apiUrl || 'https://codereview-backend.onrender.com/api'
};
