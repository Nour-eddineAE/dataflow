FROM python:3.12-slim
LABEL org.dataflow.component=tower
ENV DAGSTER_HOME=/opt/dagster/home \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
WORKDIR /opt/dagster/app
COPY pyproject.toml ./
COPY src src
ARG EXTRAS=""
RUN pip install --no-cache-dir -e ".${EXTRAS:+[$EXTRAS]}"
COPY dagster.yaml workspace.yaml /opt/dagster/home/
