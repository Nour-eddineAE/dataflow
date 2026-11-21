FROM python:3.12-slim
RUN pip install --no-cache-dir dagster==1.13.26 dagster-webserver==1.13.26 dagster-postgres==0.29.26 dagster-docker==0.29.26 "sqlalchemy<2.1"
ENV DAGSTER_HOME=/opt/dagster/home
WORKDIR /opt/dagster/app
COPY dagster.yaml workspace.yaml /opt/dagster/home/
COPY defs.py /opt/dagster/app/
