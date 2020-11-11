/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.extensions.sql.meta.provider.kafka;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import org.apache.beam.sdk.extensions.sql.meta.BeamSqlTable;
import org.apache.beam.sdk.extensions.sql.meta.Table;
import org.apache.beam.sdk.extensions.sql.meta.provider.InMemoryMetaTableProvider;
import org.apache.beam.sdk.extensions.sql.meta.provider.TableProvider;
import org.apache.beam.sdk.schemas.Schema;

/**
 * Kafka table provider.
 *
 * <p>A sample of text table is:
 *
 * <pre>{@code
 * CREATE TABLE ORDERS(
 *   ID INT COMMENT 'this is the primary key',
 *   NAME VARCHAR(127) COMMENT 'this is the name'
 * )
 * COMMENT 'this is the table orders'
 * LOCATION 'kafka://localhost:2181/brokers?topic=test'
 * TBLPROPERTIES '{"bootstrap.servers":"localhost:9092", "topics": ["topic1", "topic2"]}'
 * }</pre>
 */
@AutoService(TableProvider.class)
public class KafkaTableProvider extends InMemoryMetaTableProvider {

  private enum PayloadFormat {
    CSV,
    AVRO,
    JSON,
    PROTO
  }

  @Override
  public BeamSqlTable buildBeamSqlTable(Table table) {
    Schema schema = table.getSchema();

    JSONObject properties = table.getProperties();
    String bootstrapServers = properties.getString("bootstrap.servers");
    JSONArray topicsArr = properties.getJSONArray("topics");
    List<String> topics = new ArrayList<>(topicsArr.size());
    for (Object topic : topicsArr) {
      topics.add(topic.toString());
    }

    PayloadFormat payloadFormat =
        properties.containsKey("format")
            ? PayloadFormat.valueOf(properties.getString("format").toUpperCase())
            : PayloadFormat.CSV;

    switch (payloadFormat) {
      case CSV:
        return new BeamKafkaCSVTable(schema, bootstrapServers, topics);
      case AVRO:
        return new BeamKafkaAvroTable(schema, bootstrapServers, topics);
      case JSON:
        return new BeamKafkaJsonTable(schema, bootstrapServers, topics);
      case PROTO:
        String protoClassName = properties.getString("protoClass");
        try {
          Class<?> protoClass = Class.forName(protoClassName);
          return new BeamKafkaProtoTable(schema, bootstrapServers, topics, protoClass);
        } catch (ClassNotFoundException e) {
          throw new IllegalArgumentException("Incorrect proto class provided: " + protoClassName);
        }
      default:
        throw new IllegalArgumentException("Unsupported payload format: " + payloadFormat);
    }
  }

  @Override
  public String getTableType() {
    return "kafka";
  }
}