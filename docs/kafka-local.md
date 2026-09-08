# Local Kafka

This setup runs one Kafka broker locally. A **broker** stores topic data and serves producer and consumer requests. The same node also runs the **controller**, which manages Kafka metadata and partition leadership.

Kafka runs in **KRaft** mode: the Kafka Raft metadata quorum replaces ZooKeeper. This local setup has one combined broker/controller node, with a separate internal controller listener on port 9093. The broker is available from the host on `localhost:9092`.

A **topic** is a named event stream. `civic-reports.raw` has three **partitions**, independent ordered logs that allow consumers to scale. An **offset** is a record's sequential position within one partition. A **producer** writes records to a topic; a **consumer** reads records from it. The backend consumer belongs by default to the `civic-signal-report-processor-v1` **consumer group**, whose committed offsets track its progress per partition.

For a new consumer group, `earliest` starts at the oldest retained records, while `latest` starts only with new records. This project uses `earliest` for predictable local processing. Auto-commit is disabled, and Spring Kafka commits each record only after the listener returns successfully.

The local replication factor is 1 because there is only one broker. Production should use multiple brokers, replication greater than 1, appropriate minimum in-sync replicas, secure listeners, access control, monitoring, and capacity planning. This Compose configuration is therefore not a production configuration.

The report consumer indexes a valid event into Elasticsearch before its record offset is committed. If indexing fails, the listener fails too, so Kafka does not silently mark the event as processed.

For optional local demonstration data, see [the synthetic source guide](synthetic-data-source.md). Both sources publish to the same raw topic.

For local error handling, `civic-reports.dlt` stores records that cannot be processed after at most three attempts, or immediately when the input is permanently invalid such as malformed JSON. See [Kafka error handling](kafka-error-handling.md).

## Commands

Run all commands from the repository root.

Start Kafka and create the topic:

```powershell
docker compose up -d
```

View container status:

```powershell
docker compose ps
```

List topics:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
```

Describe the topic:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic civic-reports.raw
```

Produce a message manually:

```powershell
'{"eventId":"manual-001","eventType":"REPORT_DISCOVERED","reportId":"TEST-001"}' | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic civic-reports.raw
```

Consume messages from the beginning:

```powershell
docker compose exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic civic-reports.raw --from-beginning
```

View the backend consumer group's offsets:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group civic-signal-report-processor-v1
```

View DLT records:

```powershell
docker compose exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic civic-reports.dlt --from-beginning
```

Stop the services while preserving Kafka data:

```powershell
docker compose down
```
