import axiosInstance from "./axios";

export const getAllChatBots = () => {
  return axiosInstance.get("/chatbots");
};

export const getChatBotById = (id) => {
  return axiosInstance.get(`/chatbots/${id}`);
};

export const createChatBot = (chatBot) => {
  return axiosInstance.post("/chatbots/add", chatBot);
};

export const updateChatBot = (id, chatBot) => {
  return axiosInstance.put(`/chatbots/${id}`, chatBot);
};