FROM python:3.10-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY service.py .
COPY src ./src
COPY models ./models
USER 65534
EXPOSE 8002
CMD ["python", "service.py"]
