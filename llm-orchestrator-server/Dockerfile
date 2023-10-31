FROM python:3

RUN pip install poetry==1.7.0

ENV POETRY_NO_INTERACTION=1 \
    POETRY_VIRTUALENVS_IN_PROJECT=1 \
    POETRY_VIRTUALENVS_CREATE=0 \
    POETRY_CACHE_DIR=/tmp/poetry_cache

WORKDIR /app

COPY target/main/python/app/pyproject.toml /app

RUN poetry install --without dev --no-root && rm -rf $POETRY_CACHE_DIR

COPY target/main/python/app /app

CMD ["uvicorn", "src.app.main:app", "--host", "0.0.0.0", "--port", "8000"]