from fastapi import FastAPI

from services.embedding_service import embed, embed_query
from websocket.voice_socket import router as voice_router

app = FastAPI()

app.include_router(voice_router)

print("APP LOADED")


@app.post("/embed")
def embedding(data: dict):
    return embed(data)


@app.post("/embed_query")
def embedding_query(data: dict):
    return embed_query(data)