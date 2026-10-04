import { createContext, useContext, useEffect, useMemo, useState } from "react";

const AuthContext = createContext();

// The JWT's subject is the user's email (see JwtService.generateToken on
// the backend) - decode it client-side instead of making a round trip
// just to show who's logged in. Returns null for a missing/malformed
// token rather than throwing, since this only ever drives display.
function decodeEmailFromToken(token) {
  if (!token) return null;

  try {
    const payload = token.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json).sub ?? null;
  } catch {
    return null;
  }
}

function readStoredToken() {
  // "Remember me" decides which of these login() wrote to - check both
  // so either path resumes the session the same way.
  return localStorage.getItem("token") || sessionStorage.getItem("token");
}

export const AuthProvider = ({ children }) => {
  const [token, setToken] = useState(readStoredToken());

  // remember defaults to true so a direct localStorage.setItem (or a
  // future "silent" login) still persists across restarts.
  const login = (jwtToken, remember = true) => {
    if (remember) {
      localStorage.setItem("token", jwtToken);
      sessionStorage.removeItem("token");
    } else {
      sessionStorage.setItem("token", jwtToken);
      localStorage.removeItem("token");
    }
    setToken(jwtToken);
  };

  const logout = () => {
    localStorage.removeItem("token");
    sessionStorage.removeItem("token");
    setToken(null);
  };

  useEffect(() => {
    setToken(readStoredToken());
  }, []);

  const email = useMemo(() => decodeEmailFromToken(token), [token]);

  return (
    <AuthContext.Provider
      value={{
        token,
        email,
        isAuthenticated: !!token,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  return useContext(AuthContext);
};
