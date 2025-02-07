import com.google.protobuf.ByteString;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.signer.Signer;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.Record;
import prometheus.WriteRequest;
import prometheus.TimeSeries;
import prometheus.TimeSeries.Sample;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class KinesisToPrometheusLambda {

    private static final String REGION = "us-west-2"; // Change to your region
    private static final String STREAM_NAME = "your-kinesis-stream"; // Replace with your stream name
    private static final String WORKSPACE_ID = "ws-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"; // Your AMP workspace ID
    private static final String PROMETHEUS_URL = "https://aps-workspaces." + REGION + ".amazonaws.com/workspaces/" + WORKSPACE_ID + "/api/v1/write";

    private static final KinesisClient kinesisClient = KinesisClient.builder()
            .region(Region.of(REGION))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .httpClient(ApacheHttpClient.create())
            .build();

    public void handleRequest() {
        String shardIterator = getShardIterator();
        if (shardIterator != null) {
            List<Record> records = getRecords(shardIterator);
            for (Record record : records) {
                processRecord(record);
            }
        }
    }

    private String getShardIterator() {
        try {
            return kinesisClient.getShardIterator(
                    GetShardIteratorRequest.builder()
                            .streamName(STREAM_NAME)
                            .shardId("shardId-000000000000")
                            .shardIteratorType("TRIM_HORIZON")
                            .build()
            ).shardIterator();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<Record> getRecords(String shardIterator) {
        return kinesisClient.getRecords(
                GetRecordsRequest.builder()
                        .shardIterator(shardIterator)
                        .limit(10)
                        .build()
        ).records();
    }

    private void processRecord(Record record) {
        try {
            String data = new String(record.data().asByteArray(), StandardCharsets.UTF_8);
            System.out.println("Received Kinesis record: " + data);

            double metricValue = extractMetricValue(data);
            sendToPrometheus(metricValue);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private double extractMetricValue(String data) {
        try {
            return Double.parseDouble(data.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void sendToPrometheus(double value) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(PROMETHEUS_URL);

            // Construct Protobuf payload
            WriteRequest.Builder writeRequest = WriteRequest.newBuilder();

            TimeSeries.Builder timeSeries = TimeSeries.newBuilder();
            timeSeries.addLabels(TimeSeries.Label.newBuilder()
                    .setName("__name__")
                    .setValue("kinesis_metric"));

            timeSeries.addSamples(Sample.newBuilder()
                    .setValue(value)
                    .setTimestamp(Instant.now().toEpochMilli()));

            writeRequest.addTimeseries(timeSeries.build());

            // Convert to Protobuf binary
            byte[] protobufData = writeRequest.build().toByteArray();

            request.setEntity(new ByteArrayEntity(protobufData));
            request.setHeader("Content-Type", "application/x-protobuf");
            request.setHeader("Content-Encoding", "snappy");

            // Sign request using AWS SigV4
            Signer signer = software.amazon.awssdk.auth.signer.Aws4Signer.create();
            signer.sign(request, DefaultCredentialsProvider.create().resolveCredentials());

            // Execute the request
            client.execute(request);
            System.out.println("Metric sent to Prometheus: " + value);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

