package com.datamountaineer.streamreactor.connect.transforms

import java.util.Date

import com.datamountaineer.streamreactor.connect.transforms.CSVStringFieldToArray.Value
import org.apache.kafka.connect.data._
import org.apache.kafka.connect.source.SourceRecord
import org.apache.kafka.connect.transforms.util.Requirements.requireStruct
// import org.scalatest.{Matchers, WordSpec}
import org.scalatest._
import org.scalatest.wordspec.AnyWordSpec
import matchers.should._

// import scala.collection.JavaConversions._
import scala.jdk.CollectionConverters._

class TestCSVStringFieldToArray extends AnyWordSpec with Matchers {
  val OPTIONAL_TIMESTAMP_SCHEMA = Timestamp.builder().optional().build()
  val OPTIONAL_DECIMAL_SCHEMA = Decimal.builder(18).optional().build()
  val APP_STRING_SCHEMA = Schema.STRING_SCHEMA
  val HUB_STRING_SCHEMA = Schema.STRING_SCHEMA

  private val DELIMITER_CONFIG = "delimiter"
  private val FIELDS_CONFIG = "fields"

  "should convert single field with multiple values to array" in {
    val transform = new Value[SourceRecord];
    transform.configure(Map(
      DELIMITER_CONFIG -> ",",
      FIELDS_CONFIG -> "testcsvstring").asJava
    )

    val transformedRecord = transform.apply(mockOneFieldRecord(true));

    val value = requireStruct(transformedRecord.value, null)
    val schema = transformedRecord.valueSchema

    schema.field("testcsvstring").schema().`type`().getName shouldBe "array"
    // value.get("testcsvstring") should equal(["@wapa.com","@wapa.gov","@wapa.org"])

  }

  "should convert multiple fields with multiple values to arrays" in {
    val transform = new Value[SourceRecord];
    transform.configure(Map(
      DELIMITER_CONFIG -> ",",
      FIELDS_CONFIG -> "testcsvstring1,testcsvstring2").asJava
    )

    val transformedRecord = transform.apply(mockTwoFieldRecord(true));

    val value = requireStruct(transformedRecord.value, null)
    val schema = transformedRecord.valueSchema

    schema.field("testcsvstring1").schema().`type`().getName shouldBe "array"
    // value.get("testcsvstring1") should equal(["@wapa.com","@wapa.gov","@wapa.org"])

    schema.field("testcsvstring2").schema().`type`().getName shouldBe "array"
    // value.get("testcsvstring2") should equal(["@kcnsc.gov","@kcp.com"])
  }

  "should convert fields with nulls to arrays" in {
    val transform = new Value[SourceRecord];
    transform.configure(Map(
      DELIMITER_CONFIG -> ",",
      FIELDS_CONFIG -> "testcsvstring").asJava
    )

    val transformedRecord = transform.apply(mockNullRecord(true));

    val value = requireStruct(transformedRecord.value, null)
    val schema = transformedRecord.valueSchema

    schema.field("testcsvstring").schema().`type`().getName shouldBe "array"
    // value.getArray("testcsvstring1").length should equal 0
    // value.get("testcsvstring1") should equal([])
  }

  private def mockOneFieldRecord(withSchema: Boolean) = {
    val simpleStructSchema = SchemaBuilder.struct.name("name").version(1).doc("doc")
      .field("code", Schema.STRING_SCHEMA)
      .field("name", Schema.STRING_SCHEMA)
      .field("type_code", Schema.STRING_SCHEMA)
      .field("description", Schema.STRING_SCHEMA)
      .field("testcsvstring", Schema.OPTIONAL_STRING_SCHEMA)
      .build

    val simpleStruct = new Struct(simpleStructSchema)
      .put("code", "TEST")
      .put("name", "NAME")
      .put("type_code", "APP")
      .put("description", "This should not make it")
      .put("testcsvstring", "@wapa.com,@wapa.gov,@wapa.org")

    new SourceRecord(null, null, "test", 0, if (withSchema) simpleStructSchema else null, simpleStruct)
  }

  private def mockTwoFieldRecord(withSchema: Boolean) = {
    val simpleStructSchema = SchemaBuilder.struct.name("name").version(1).doc("doc")
      .field("code", Schema.STRING_SCHEMA)
      .field("name", Schema.STRING_SCHEMA)
      .field("type_code", Schema.STRING_SCHEMA)
      .field("description", Schema.STRING_SCHEMA)
      .field("testcsvstring1", Schema.OPTIONAL_STRING_SCHEMA)
      .field("testcsvstring2", Schema.OPTIONAL_STRING_SCHEMA)
      .build

    val simpleStruct = new Struct(simpleStructSchema)
      .put("code", "TEST")
      .put("name", "NAME")
      .put("type_code", "HUB")
      .put("description", "This should not make it")
      .put("testcsvstring1", "@wapa.com,@wapa.gov,@wapa.org")
      .put("testcsvstring2", "@kcnsc.gov,@kcp.com")

    new SourceRecord(null, null, "test", 0, if (withSchema) simpleStructSchema else null, simpleStruct)
  }

  private def mockNullRecord(withSchema: Boolean) = {
    val simpleStructSchema = SchemaBuilder.struct.name("name").version(1).doc("doc")
      .field("code", Schema.STRING_SCHEMA)
      .field("name", Schema.STRING_SCHEMA)
      .field("type_code", Schema.STRING_SCHEMA)
      .field("description", Schema.STRING_SCHEMA)
      .field("testcsvstring", Schema.OPTIONAL_STRING_SCHEMA)
      .build

    val simpleStruct = new Struct(simpleStructSchema)
      .put("code", "TEST")
      .put("name", "NAME")
      .put("type_code", "HUB")
      .put("description", "This should not make it")
      .put("testcsvstring", null)

    new SourceRecord(null, null, "test", 0, if (withSchema) simpleStructSchema else null, simpleStruct)
  }
}