package com.datamountaineer.streamreactor.connect.transforms

import java.util
import java.util.List

import org.apache.kafka.common.cache.{Cache, LRUCache, SynchronizedCache}
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.connect.connector.ConnectRecord
import org.apache.kafka.connect.data.{Schema, SchemaBuilder, Struct}
import org.apache.kafka.connect.transforms.Transformation
import org.apache.kafka.connect.transforms.util.Requirements.{requireMap, requireStruct}
import org.apache.kafka.connect.transforms.util.{SchemaUtil, SimpleConfig}

// import scala.collection.JavaConversions._
import scala.jdk.CollectionConverters._

object NestingSPTypeFields {
  private val PURPOSE = "nesting fields from value to new field for sp types"
  private val NESTED_NAME_TYPE_CONFIG = "nested.typename"
  private val FIELDS_CONFIG = "fields"
  private val ORIG_TYPE_CODE = "type_code"
  private val TYPE_SCHEMA_NAME = "type"
  private val TYPE_DESCRIPTION = "description"
  private val TYPE_CODE = "code"
  private val APP_DESCRIPTION = "Application"
  private val HUB_DESCRIPTION = "Authentication Hub"

  private val CONFIG_DEF: ConfigDef = new ConfigDef()
    .define(NESTED_NAME_TYPE_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "Nested field name.")
    .define(FIELDS_CONFIG, ConfigDef.Type.LIST, ConfigDef.NO_DEFAULT_VALUE, ConfigDef.Importance.HIGH, "Field names to add in the nested field.")

  class Key[R <: ConnectRecord[R]] extends NestingSPTypeFields[R] {
    override protected def operatingSchema(record: R): Schema = record.keySchema

    override protected def operatingValue(record: R): Any = record.key

    override protected def newRecord(record: R, updatedSchema: Schema, updatedValue: Any): R =
      record.newRecord(record.topic, record.kafkaPartition, updatedSchema, updatedValue, record.valueSchema, record.value, record.timestamp)
  }

  class Value[R <: ConnectRecord[R]] extends NestingSPTypeFields[R] {
    override protected def operatingSchema(record: R): Schema = record.valueSchema

    override protected def operatingValue(record: R): Any = record.value

    override protected def newRecord(record: R, updatedSchema: Schema, updatedValue: Any): R =
      record.newRecord(record.topic, record.kafkaPartition, record.keySchema, record.key, updatedSchema, updatedValue, record.timestamp)
  }

}

abstract class NestingSPTypeFields[R <: ConnectRecord[R]] extends Transformation[R] {
  private var fields: List[String] = _
  private var nestedName: String = _
  private var schemaUpdateCache: Cache[Schema, Schema] = _

  override def configure(props: util.Map[String, _]): Unit = {
    val config = new SimpleConfig(NestingSPTypeFields.CONFIG_DEF, props)
    // nestedName = config.getString(NestingSPTypeFields.NESTED_NAME_CONFIG)
    nestedName = NestingSPTypeFields.TYPE_SCHEMA_NAME
    fields = config.getList(NestingSPTypeFields.FIELDS_CONFIG)
    schemaUpdateCache = new SynchronizedCache[Schema, Schema](new LRUCache[Schema, Schema](16))
  }

  override def apply(record: R): R =
    if (operatingSchema(record) == null) applySchemaless(record) else applyWithSchema(record)

  private def applySchemaless(record: R) = {
    val value = requireMap(operatingValue(record), NestingSPTypeFields.PURPOSE)
    val updatedValue = new util.HashMap[String, AnyRef](value)

    // println("applySchemaless1 -- Value: ", value, "Updated Value: ", updatedValue)

    updatedValue.put(nestedName, value.asScala.filterKeys(k => fields.contains(k)))
    // println("applySchemaless2 -- Value: ", value, "Updated Value: ", updatedValue)
    newRecord(record, null, updatedValue)
  }

  private def applyWithSchema(record: R) = {
    val value = requireStruct(operatingValue(record), NestingSPTypeFields.PURPOSE)
    var updatedSchema = schemaUpdateCache.get(value.schema)
    if (updatedSchema == null) {
      updatedSchema = makeUpdatedSchema(value.schema)
      schemaUpdateCache.put(value.schema, updatedSchema)
    }
    val newNestedSchema = updatedSchema.field(nestedName).schema
    val newNestedValue = new Struct(newNestedSchema)
    val updatedValue = new Struct(updatedSchema)
    // println("applyWithSchema1 -- Value: ", value, "Updated Value: ", updatedValue)
    
    for (field <- value.schema.fields.asScala) {
      // println(s"FIELD NAME: ${field.name}")
      updatedValue.put(field.name, value.get(field))
    }
    
    for (field <- value.schema.fields.asScala) {
      if (fields.contains(field.name)) {
        newNestedValue.put(NestingSPTypeFields.TYPE_CODE, value.get(field))
        if (value.get(field) == "HUB") {
          newNestedValue.put(NestingSPTypeFields.TYPE_DESCRIPTION, "Authentication Hub")
        } else {
          newNestedValue.put(NestingSPTypeFields.TYPE_DESCRIPTION, "Application")
        }
      }
    }
    updatedValue.put(nestedName, newNestedValue)

    // println("applyWithSchema3 -- Value: ", value, "Updated Value: ", updatedValue)

    newRecord(record, updatedSchema, updatedValue)
  }

  private def makeUpdatedSchema(schema: Schema) = {
    val builder = SchemaUtil.copySchemaBasics(schema, SchemaBuilder.struct)
    val nestedStruct = SchemaBuilder.struct

    // import scala.collection.JavaConversions._
    import scala.jdk.CollectionConverters._
    // import scala.jdk.javaapi.CollectionConverters._

    nestedStruct.field(NestingSPTypeFields.TYPE_CODE, Schema.STRING_SCHEMA)
    nestedStruct.field(NestingSPTypeFields.TYPE_DESCRIPTION, Schema.STRING_SCHEMA)
    builder.field(nestedName, nestedStruct)
    // println(s"Builder2: ${builder.schema.toString}")
    for (field <- schema.fields.asScala) {
      // if (field.name != NestingSPTypeFields.ORIG_TYPE_CODE)
      builder.field(field.name, field.schema)
    }
    builder.build
  }

  override def close(): Unit = {
    schemaUpdateCache = null
  }

  override def config: ConfigDef = NestingSPTypeFields.CONFIG_DEF

  protected def operatingSchema(record: R): Schema

  protected def operatingValue(record: R): Any

  protected def newRecord(record: R, updatedSchema: Schema, updatedValue: Any): R
}
