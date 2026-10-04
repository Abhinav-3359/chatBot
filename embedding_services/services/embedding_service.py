from fastembed import TextEmbedding

model = TextEmbedding(
    model_name="BAAI/bge-base-en-v1.5"
)

def embed(data):

    text = data["text"]

    embedding = next(model.embed([text])).tolist()

    return {
        "embedding": embedding
    }


def embed_query(data):

    text = "query: " + data["text"]

    embedding = next(model.embed([text])).tolist()

    return {
        "embedding": embedding
    }