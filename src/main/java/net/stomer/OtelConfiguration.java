/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package net.stomer;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

/**
 * All SDK management takes place here, away from the instrumentation code, which should only access
 * the OpenTelemetry APIs.
 */
class OtelConfiguration {
    private static final String OTEL_EXPORTER_OTLP_ENDPOINT = Optional.ofNullable(System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")).orElse("john");
  /**
   * Initializes the OpenTelemetry SDK with a logging span exporter and the W3C Trace Context
   * propagator.
   *
   * @return A ready-to-use {@link OpenTelemetry} instance.
   */
  static OpenTelemetry initOpenTelemetry() {
    Resource resource =
        Resource.getDefault()
            .merge(Resource.builder().put(SERVICE_NAME, "AkamaiMetrics").build());

    OpenTelemetrySdk openTelemetrySdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setResource(resource)
                    .addSpanProcessor(
                        BatchSpanProcessor.builder(
                                OtlpGrpcSpanExporter.builder()
                                    .setEndpoint(OTEL_EXPORTER_OTLP_ENDPOINT)
                                    .setTimeout(2, TimeUnit.SECONDS)
                                    .build())
                            .setScheduleDelay(1000, TimeUnit.MILLISECONDS)
                            .build())
                    .build())
            .setMeterProvider(
                SdkMeterProvider.builder()
                    .setResource(resource)
                    .registerMetricReader(
                        PeriodicMetricReader.builder(OtlpGrpcMetricExporter.builder().setEndpoint(OTEL_EXPORTER_OTLP_ENDPOINT).build())
                            .setInterval(Duration.ofMillis(10000))
                            .build())
                    .build())
            .buildAndRegisterGlobal();

    Runtime.getRuntime().addShutdownHook(new Thread(openTelemetrySdk::close));

    return openTelemetrySdk;
  }
}
