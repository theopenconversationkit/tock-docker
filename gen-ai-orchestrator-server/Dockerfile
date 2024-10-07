FROM python:3.11-alpine

# Set environment variables
ENV PYTHONUNBUFFERED=1 \
    POETRY_VERSION=1.8.3 \
    # Poetry virtual environments are disabled to install packages globally inside the container.
    POETRY_VIRTUALENVS_CREATE=false \
    POETRY_NO_INTERACTION=1 \
    # The Poetry cache directory is set to reduce repeated downloads in future builds.
    POETRY_CACHE_DIR='/var/cache/pypoetry'

# Install Poetry
RUN pip install "poetry==$POETRY_VERSION"

# Set working directory in the container
WORKDIR /app

# Copy only the necessary files to the Docker container
COPY target/server/pyproject.toml /app/
COPY target/server/poetry.lock /app/

# Install dependencies
RUN poetry install --no-root --no-dev --verbose

# Copy the rest of the project files into the container
COPY target/server/src/gen_ai_orchestrator gen_ai_orchestrator/

# Expose port for web app
EXPOSE 8000

# Run the application
CMD ["uvicorn", "gen_ai_orchestrator.main:app", "--host", "0.0.0.0", "--port", "8000", "--log-config", "gen_ai_orchestrator/configurations/logging/config.ini"]
