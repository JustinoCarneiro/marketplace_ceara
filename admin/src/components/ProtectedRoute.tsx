import { Navigate, Outlet } from 'react-router-dom';
import { isAuthenticated } from '../store/auth';

export default function ProtectedRoute() {
  return isAuthenticated() ? <Outlet /> : <Navigate to="/login" replace />;
}
