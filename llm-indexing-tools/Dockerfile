FROM python:3.11-alpine

WORKDIR /app

ENV HTTP_PROXY http://172.17.0.1:3128
ENV HTTPS_PROXY http://172.17.0.1:3128
ENV http_proxy http://172.17.0.1:3128
ENV https_proxy http://172.17.0.1:3128

# --- Install Gen AI server with it's requirements ---
# Need to have the server because of the model dependency :(
COPY target/gen_ai_orchestrator gen_ai_orchestrator/
COPY target/requirements.txt gen_ai_orchestrator/
RUN pip install --no-cache-dir -r gen_ai_orchestrator/requirements.txt
ENV PYTHONPATH "${PYTHONPATH}:/app/"

# --- Install Toolings ---
COPY target/tock-llm-indexing-tools tock-llm-indexing-tools/
RUN pip install --no-cache-dir -r tock-llm-indexing-tools/requirements.txt
ENV PYTHONPATH "${PYTHONPATH}:/app/tock-llm-indexing-tools/"

# Use it with 
# docker run --rm -it tock/llm-indexing-tools:latest-SNAPSHOT python tock-llm-indexing-tools/index_documents.py -h
