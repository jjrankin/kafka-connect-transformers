package com.datamountaineer.streamreactor.connect.transforms

import java.util.Date

import com.datamountaineer.streamreactor.connect.transforms.NestingSPTypeFields.Value
import org.apache.kafka.connect.data._
import org.apache.kafka.connect.source.SourceRecord
import org.apache.kafka.connect.transforms.util.Requirements.requireStruct
// import org.scalatest.{Matchers, WordSpec}
import org.scalatest._
import org.scalatest.wordspec.AnyWordSpec
import matchers.should._

// import scala.collection.JavaConversions._
import scala.jdk.CollectionConverters._

class TestNestingSPTypeFields extends AnyWordSpec with Matchers {
  val OPTIONAL_TIMESTAMP_SCHEMA = Timestamp.builder().optional().build()
  val OPTIONAL_DECIMAL_SCHEMA = Decimal.builder(18).optional().build()
  val APP_STRING_SCHEMA = Schema.STRING_SCHEMA
  val HUB_STRING_SCHEMA = Schema.STRING_SCHEMA

  private val NESTED_NAME_CONFIG = "nested.typename"
  private val FIELDS_CONFIG = "fields"

  "should append another field with two nested fields when have schema for APP" in {
    val transform = new Value[SourceRecord];
    transform.configure(Map(
      NESTED_NAME_CONFIG -> "type",
      FIELDS_CONFIG -> "type_code").asJava
    )

    val transformedRecord = transform.apply(mockAppRecord(true));

    val value = requireStruct(transformedRecord.value, null)
    val schema = transformedRecord.valueSchema

    val nestedSchema = schema.field("type").schema()
    val nestedValue =  requireStruct(value.get("type"), null)

    println("NestedValue: ", nestedValue, " Value: ", value)

    nestedSchema.field("code").schema().`type`().getName shouldBe "string"
    nestedValue.get("code") shouldBe value.get("type_code")

    nestedSchema.field("description").schema().`type`().getName shouldBe "string"
    nestedValue.get("description") shouldBe "Application"
  }

  "should append another field with two nested fields when have schema for HUB" in {
    val transform = new Value[SourceRecord];
    transform.configure(Map(
      NESTED_NAME_CONFIG -> "type",
      FIELDS_CONFIG -> "type_code").asJava
    )

    val transformedRecord = transform.apply(mockHubRecord(true));

    val value = requireStruct(transformedRecord.value, null)
    val schema = transformedRecord.valueSchema

    val nestedSchema = schema.field("type").schema()
    val nestedValue =  requireStruct(value.get("type"), null)

    nestedSchema.field("code").schema().`type`().getName shouldBe "string"
    nestedValue.get("code") shouldBe value.get("type_code")

    nestedSchema.field("description").schema().`type`().getName shouldBe "string"
    nestedValue.get("description") shouldBe "Authentication Hub"
  }

  private def mockAppRecord(withSchema: Boolean) = {
    val simpleStructSchema = SchemaBuilder.struct.name("name").version(1).doc("doc")
      .field("code", Schema.STRING_SCHEMA)
      .field("name", Schema.STRING_SCHEMA)
      .field("type_code", Schema.STRING_SCHEMA)
      .field("description", Schema.STRING_SCHEMA)
      .build

    val simpleStruct = new Struct(simpleStructSchema)
      .put("code", "TEST")
      .put("name", "NAME")
      .put("type_code", "APP")
      .put("description", "This should not make it")

    new SourceRecord(null, null, "test", 0, if (withSchema) simpleStructSchema else null, simpleStruct)
  }

  private def mockHubRecord(withSchema: Boolean) = {
    val simpleStructSchema = SchemaBuilder.struct.name("name").version(1).doc("doc")
      .field("code", Schema.STRING_SCHEMA)
      .field("name", Schema.STRING_SCHEMA)
      .field("type_code", Schema.STRING_SCHEMA)
      .field("description", Schema.STRING_SCHEMA)
      .build

    val simpleStruct = new Struct(simpleStructSchema)
      .put("code", "TEST")
      .put("name", "NAME")
      .put("type_code", "HUB")
      .put("description", "This should not make it")

    new SourceRecord(null, null, "test", 0, if (withSchema) simpleStructSchema else null, simpleStruct)
  }

}