import axiosInstance from "./axios";

export const askQuestion = (chatBotId, message) => {
  return axiosInstance.post(`/chatbots/${chatBotId}/chat`, {
    message,
  });
};

export const getChatHistory = (chatBotId) => {
  return axiosInstance.get(`/chatbots/${chatBotId}/chat/history`);
};
