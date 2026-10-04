import axiosInstance from "./axios";

export const getDocuments = (chatBotId) => {
  return axiosInstance.get(`/documents/${chatBotId}`);
};

export const deleteDocument = (chatBotId, documentId) => {
  return axiosInstance.delete(`/documents/${chatBotId}/${documentId}`);
};

export const uploadDocument = (chatBotId, file) => {
  const formData = new FormData();
  formData.append("file", file);

  return axiosInstance.post(
    `/documents/add/${chatBotId}`,
    formData,
    {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    }
  );
};