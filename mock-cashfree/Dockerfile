FROM python:3.11-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

RUN addgroup --system --gid 10001 bolpay && adduser --system --uid 10001 --ingroup bolpay bolpay \
    && chown -R bolpay:bolpay /app
USER 10001:10001

EXPOSE 9000

CMD ["sh", "-c", "exec gunicorn --bind 0.0.0.0:${PORT:-9000} --workers 1 --threads 4 --timeout 60 app:app"]
