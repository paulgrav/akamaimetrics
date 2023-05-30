# AkamaiMetrics

## About

Google Function to process Akamai Datastream files and emit Open Telemetry data.

## Deployment

```
gcloud functions deploy akamaimetrics \
      --gen2 \
      --trigger-event-filters="type=google.cloud.storage.object.v1.finalized" \
      --trigger-event-filters="bucket=BUCKET_NAME" \
      --entry-point net.stomer.AkamaiMetrics --runtime java17 \
      --set-env-vars=OTEL_EXPORTER_OTLP_ENDPOINT=http://xx.xx.xx.xx:4317 
      --set-env-vars=OTEL_SERVICE_NAME=AkamaiMetrics  
      --trigger-location=europe-west1 
```

## Build

`./mvnw package`

## Local development

The the function assumes there is an Open Telemetry collector listening on localhost:4137. 

`docker compose up` will startup [jaeger all-in-one]([url](https://www.jaegertracing.io/docs/1.45/getting-started/)) 
which accepts otlp data on 4317.

```
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
export OTEL_SERVICE_NAME=AkamaiMetrics
export OTEL_METRICS_EXPORTER=none
./mvnw function:run -Drun.functionTarget=net.stomer.AkamaiMetrics
```

The function can be called using `curl`

```
curl -m 70 -X POST http://localhost:8082 \
      -H "Content-Type: application/json" \
      -H "ce-id: 1234567890" \
      -H "ce-specversion: 1.0" \
      -H "ce-type: google.cloud.storage.object.v1.finalized" \
      -H "ce-time: 2020-08-08T00:11:44.895529672Z" \
      -H "ce-source: //storage.googleapis.com/projects/_/buckets/BUCKET_NAME" \
      -d '{
    "name": "logs_2023_02_24_12_28902_ak-835221-1677240183-459530-ds.gz",
    "bucket": "BUCKET_NAME",
    "contentType": "application/json",
    "metageneration": "1",
    "timeCreated": "2020-04-23T07:38:57.230Z",
    "updated": "2020-04-23T07:38:57.230Z"
  }'
```

Traces are emitted via otlp and can be viewed at http://localhost:16686
 
