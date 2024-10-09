FROM python:3.11-slim

# Mettre à jour le système et installer libpq-dev et autres dépendances utiles
RUN apt-get update && apt-get install -y \
    libpq-dev \
    build-essential \
    gcc \
    && rm -rf /var/lib/apt/lists/* \

WORKDIR /app

COPY target/requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY target/gen_ai_orchestrator gen_ai_orchestrator/

EXPOSE 8000

CMD ["uvicorn", "gen_ai_orchestrator.main:app", "--host", "0.0.0.0", "--port", "8000", "--log-config", "gen_ai_orchestrator/configurations/logging/config.ini"]