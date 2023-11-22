FROM python:3.11-alpine

WORKDIR /app

COPY target/requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY target/llm_orchestrator llm_orchestrator/

EXPOSE 8000

CMD ["uvicorn", "llm_orchestrator.main:app", "--host", "0.0.0.0", "--port", "8000"]