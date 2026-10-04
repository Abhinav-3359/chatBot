import axiosInstance from "./axios";

export const login = (credentials) => {
  return axiosInstance.post("/users/login", credentials);
};

export const register = (userData) => {
  return axiosInstance.post("/users/register", userData);
};