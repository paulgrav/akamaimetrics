package net.stomer;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import com.google.cloud.functions.CloudEventsFunction;
import com.google.events.cloud.storage.v1.StorageObjectData;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.cloudevents.CloudEvent;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.StorageOptions;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.util.zip.GZIPInputStream;
import org.json.*;
import java.io.RandomAccessFile;

public class AkamaiMetrics implements CloudEventsFunction {

    private static final Logger logger = Logger.getLogger(AkamaiMetrics.class.getName());
    private static final OpenTelemetry openTelemetry = OtelConfiguration.initOpenTelemetry();
    private static final Meter meter = openTelemetry.getMeter("net.stomer.AkamaiMetrics");
    private static final Tracer tracer = openTelemetry.getTracer("net.stomer.AkamaiMetrics");
    private static final Storage storage = StorageOptions.getDefaultInstance().getService();

    private static final LongCounter cloudEventCounter =
        meter
            .counterBuilder("akamai_events_accepted_count")
            .setDescription("Counts number of time accept method is called.")
            .setUnit("1")
            .build();

    private static final LongCounter akamaiHttpResponsesTotal =
        meter
            .counterBuilder("akamai_http_responses_total")
            .setDescription("Reports number https responses returned by Akamai edges.")
            .setUnit("1")
            .build();

    @Override
    public void accept(CloudEvent event) throws InvalidProtocolBufferException {
        Span span = tracer.spanBuilder("accept").setSpanKind(SpanKind.CONSUMER).startSpan();
        cloudEventCounter.add(1);
        try (Scope scope = span.makeCurrent()) {
            logger.info("Event: " + event.getId());
            logger.info("Event Type: " + event.getType());
            span.setAttribute(SemanticAttributes.CLOUDEVENTS_EVENT_ID, event.getId());
            span.setAttribute(SemanticAttributes.CLOUDEVENTS_EVENT_TYPE, event.getType());
            span.setAttribute(SemanticAttributes.CLOUDEVENTS_EVENT_SOURCE, event.getSource().toString());
            span.setAttribute(SemanticAttributes.CLOUDEVENTS_EVENT_SUBJECT, event.getSubject());
            span.setAttribute(SemanticAttributes.CLOUDEVENTS_EVENT_SPEC_VERSION, event.getSpecVersion().toString());

            if (event.getData() == null) {
                logger.warning("No data found in cloud event payload!");
                return;
            }

            String cloudEventData = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
            StorageObjectData.Builder builder = StorageObjectData.newBuilder();
            JsonFormat.parser().merge(cloudEventData, builder);
            StorageObjectData data = builder.build();

            span.setAttribute("Bucket", data.getBucket());
            span.setAttribute("File", data.getName());
            span.setAttribute("Metageneration", data.getMetageneration());
            readFile(data.getBucket(),  data.getName());
        } catch (Throwable t) {
            span.setStatus(StatusCode.ERROR, "Something bad happened!");
            span.recordException(t);
            throw t;
        } finally {
             span.end();
        }
    }

    private void processLogLine(String line) {
        if(line == null || line.trim().isEmpty()) {
            return;
        }

        Span span = tracer.spanBuilder("processLogLine").startSpan();
        try (Scope scope = span.makeCurrent()) {
            JSONObject obj = new JSONObject(line);
            Attributes attributes = Attributes.of(
                SemanticAttributes.HTTP_STATUS_CODE, Long.parseLong(obj.getString("statusCode")),
                SemanticAttributes.HTTP_METHOD, obj.getString("reqMethod"),
                SemanticAttributes.NET_PEER_NAME, obj.getString("reqHost")
                );
            akamaiHttpResponsesTotal.add(1, attributes);
        } catch (org.json.JSONException e) {
            span.setStatus(StatusCode.ERROR, "Unable to parse JSON log record");
            Attributes eventAttributes = Attributes.of(stringKey("log record"), line);
            span.addEvent("Invalid JSON", eventAttributes);
            span.recordException(e);
            span.end();
        } catch (Throwable t) {
            span.setStatus(StatusCode.ERROR, "Uncaught exception.");
            span.recordException(t);
            span.end();
            throw t;
        }
    }

    private void readFile(String bucket, String path) {
        Span span = tracer.spanBuilder("readFile").startSpan();
        try (Scope scope = span.makeCurrent()) {
            //Blob blob = storage.get(bucket, path);
            //ReadChannel reader = blob.reader();
            RandomAccessFile filereader = new RandomAccessFile("/Users/paul/Downloads/akamai.txt.gz", "r");
            span.setAttribute("bucket", bucket);
            span.setAttribute("path", path);

            // Read the text file and count the lines
            try (   BufferedReader reader =
                        new BufferedReader(
                            new InputStreamReader(
                                new GZIPInputStream(Channels.newInputStream(filereader.getChannel()))))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    processLogLine(line);
                }

            } catch (IOException e) {
                span.setStatus(StatusCode.ERROR, "IOException.");
                span.recordException(e);
            }
        } catch (FileNotFoundException e) {
            span.setStatus(StatusCode.ERROR, "FileNotFoundException");
            span.recordException(e);
        } catch (Throwable t) {
            span.setStatus(StatusCode.ERROR, "Uncaught exception");
            span.recordException(t);
            throw t;
        } finally {
             span.end();
        }
    }

}
