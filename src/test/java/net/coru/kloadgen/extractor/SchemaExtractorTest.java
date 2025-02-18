/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.kloadgen.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.squareup.wire.schema.internal.parser.TypeElement;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import net.coru.kloadgen.exception.KLoadGenException;
import net.coru.kloadgen.extractor.extractors.AvroExtractor;
import net.coru.kloadgen.extractor.extractors.JsonExtractor;
import net.coru.kloadgen.extractor.extractors.ProtoBufExtractor;
import net.coru.kloadgen.extractor.impl.SchemaExtractorImpl;
import net.coru.kloadgen.model.FieldValueMapping;
import net.coru.kloadgen.testutil.FileHelper;
import net.coru.kloadgen.testutil.ParsedSchemaUtil;
import net.coru.kloadgen.util.JMeterHelper;
import org.apache.avro.Schema.Field;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.util.JMeterUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchemaExtractorTest {

  private final FileHelper fileHelper = new FileHelper();

  @InjectMocks
  private SchemaExtractorImpl schemaExtractor;

  @Mock
  private AvroExtractor avroExtractor;

  @Mock
  private JsonExtractor jsonExtractor;

  @Mock
  private ProtoBufExtractor protoBufExtractor;

  @Mock
  private JMeterHelper jMeterHelper;

  @BeforeEach
  void setUp() {
    File file = new File("src/test/resources");
    String absolutePath = file.getAbsolutePath();
    JMeterUtils.loadJMeterProperties(absolutePath + "/kloadgen.properties");
    JMeterContext jmcx = JMeterContextService.getContext();
    jmcx.setVariables(new JMeterVariables());
    JMeterUtils.setLocale(Locale.ENGLISH);
  }

  @Test
  @DisplayName("Test flatPropertiesList with AVRO")
  void testFlatPropertiesListWithAVRO() throws IOException, RestClientException {

    File testFile = fileHelper.getFile("/avro-files/embedded-avros-example-test.avsc");

    Mockito.when(avroExtractor.getParsedSchema(Mockito.anyString())).thenCallRealMethod();
    ParsedSchema parsedSchema = schemaExtractor.schemaTypesList(testFile, "AVRO");

    Mockito.when(jMeterHelper.getParsedSchema(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(parsedSchema);
    Mockito.doNothing().when(avroExtractor).processField(Mockito.any(Field.class), Mockito.anyList(), eq(true), eq(false));

    schemaExtractor.flatPropertiesList("avroSubject");

    Mockito.verify(avroExtractor, Mockito.times(2)).processField(Mockito.any(Field.class), Mockito.anyList(), eq(true), eq(false));

  }

  @Test
  @DisplayName("Test flatPropertiesList with Json")
  void testFlatPropertiesListWithJson() throws IOException, RestClientException {

    File testFile = fileHelper.getFile("/jsonschema/basic.jcs");
    ParsedSchema parsedSchema = schemaExtractor.schemaTypesList(testFile, "JSON");

    Mockito.when(jMeterHelper.getParsedSchema(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(parsedSchema);
    Mockito.when(jsonExtractor.processSchema(Mockito.any(JsonNode.class))).thenReturn(new ArrayList<>());

    schemaExtractor.flatPropertiesList("jsonSubject");

    Mockito.verify(jsonExtractor).processSchema(Mockito.any(JsonNode.class));

  }

  @Test
  @DisplayName("Test flatPropertiesList with Protobuf")
  void testFlatPropertiesListWithProtobuf() throws RestClientException, IOException {

    File testFile = fileHelper.getFile("/proto-files/easyTest.proto");
    ParsedSchema parsedSchema = schemaExtractor.schemaTypesList(testFile, "PROTOBUF");

    Mockito.when(jMeterHelper.getParsedSchema(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(parsedSchema);
    Mockito.doNothing().when(protoBufExtractor).processField(Mockito.any(TypeElement.class), Mockito.anyList(), Mockito.anyList(), eq(false));

    schemaExtractor.flatPropertiesList("protobufSubject");

    Mockito.verify(protoBufExtractor).processField(Mockito.any(TypeElement.class), Mockito.anyList(), Mockito.anyList(), eq(false));

  }

  @Test
  @DisplayName("Test flatPropertiesList throws exception schema type not supported")
  void testFlatPropertiesListWithException() throws IOException, RestClientException {

    ParsedSchema parsedSchema = new ParsedSchemaUtil();

    Mockito.when(jMeterHelper.getParsedSchema(Mockito.anyString(), Mockito.any(Properties.class))).thenReturn(parsedSchema);

    assertThatExceptionOfType(KLoadGenException.class)
        .isThrownBy(() -> {
          Pair<String, List<FieldValueMapping>> result = schemaExtractor.flatPropertiesList("exceptionSubject");
          assertThat(result).isNull();
        })
        .withMessage(String.format("Schema type not supported %s", parsedSchema.schemaType()));
  }

}