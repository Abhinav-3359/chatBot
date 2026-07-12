from fastapi import FastAPI
from sentence_transformers import SentenceTransformer

app = FastAPI()

# Load model once at startup
model = SentenceTransformer("BAAI/bge-base-en-v1.5")

@app.post("/embed")
def embed(data: dict):

    text = data["text"]

    embedding = model.encode(text).tolist()

    return {
        "embedding": embedding
    }
    
@app.post("/embed_query")
def embed_query(data: dict):

    text = "query: " + data["text"]

    embedding = model.encode(text).tolist()

    return {
        "embedding": embedding
    }    