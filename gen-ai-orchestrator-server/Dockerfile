FROM python:3.11-alpine

WORKDIR /app

COPY target/requirements.txt .

ENV HTTP_PROXY http://172.17.0.1:3128
ENV HTTPS_PROXY http://172.17.0.1:3128
ENV http_proxy http://172.17.0.1:3128
ENV https_proxy http://172.17.0.1:3128

RUN pip install --no-cache-dir -r requirements.txt

COPY target/gen_ai_orchestrator gen_ai_orchestrator/

EXPOSE 8000

CMD ["uvicorn", "gen_ai_orchestrator.main:app", "--host", "0.0.0.0", "--port", "8000", "--log-config", "gen_ai_orchestrator/configurations/logging/config.ini"]