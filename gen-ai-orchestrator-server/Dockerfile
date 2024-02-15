FROM python:3.11-alpine

WORKDIR /app

COPY target/requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY target/gen_ai_orchestrator gen_ai_orchestrator/

EXPOSE 8000

CMD ["gunicorn", "gen_ai_orchestrator.main:app", "--bind", "0.0.0.0:8000", "--workers", "4", "--worker-class", "uvicorn.workers.UvicornWorker", "--log-config", "gen_ai_orchestrator/configurations/logging/config.ini"]