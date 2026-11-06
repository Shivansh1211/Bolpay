FROM python:3.11-slim AS model-build
WORKDIR /build
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN python generate_data.py && python train_models.py

FROM python:3.11-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
RUN addgroup --system --gid 10001 bolpay && adduser --system --uid 10001 --ingroup bolpay bolpay \
    && pip install --no-cache-dir Flask==3.0.0 gunicorn==22.0.0 numpy==1.26.2 scikit-learn==1.3.2 joblib==1.3.2
COPY --from=model-build --chown=bolpay:bolpay /build/app.py /app/app.py
COPY --from=model-build --chown=bolpay:bolpay /build/models/isolation_forest_model.pkl /app/models/isolation_forest_model.pkl
USER 10001:10001
EXPOSE 5000
CMD ["sh", "-c", "exec gunicorn --bind 0.0.0.0:${PORT:-5000} --workers 1 --threads 2 --timeout 60 app:app"]
