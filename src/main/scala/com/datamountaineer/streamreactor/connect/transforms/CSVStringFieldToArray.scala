package com.datamountaineer.streamreactor.connect.transforms

import java.util
import java.util.List

import org.apache.kafka.common.cache.{Cache, LRUCache, SynchronizedCache}
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.connect.connector.ConnectRecord
import org.apache.kafka.connect.data._
import org.apache.kafka.connect.data.{Schema, SchemaBuilder, Struct}
import org.apache.kafka.connect.transforms.Transformation
import org.apache.kafka.connect.transforms.util.Requirements.{requireMap, requireStruct}
import org.apache.kafka.connect.transforms.util.{SchemaUtil, SimpleConfig}
import collection.mutable._

// import scala.collection.JavaConversions._
import scala.jdk.CollectionConverters._

object CSVStringFieldToArray {
  private val PURPOSE = "nesting fields from value to new field for sp types"
  private val DELIMITER_CONFIG = "delimiter"
  private val FIELDS_CONFIG = "fields"

  private val CONFIG_DEF: ConfigDef = new ConfigDef()
    .define(DELIMITER_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "String Delimiter")
    .define(FIELDS_CONFIG, ConfigDef.Type.LIST, ConfigDef.NO_DEFAULT_VALUE, ConfigDef.Importance.HIGH, "Field names to convert to array")

  class Key[R <: ConnectRecord[R]] extends CSVStringFieldToArray[R] {
    override protected def operatingSchema(record: R): Schema = record.keySchema

    override protected def operatingValue(record: R): Any = record.key

    override protected def newRecord(record: R, updatedSchema: Schema, updatedValue: Any): R =
      record.newRecord(record.topic, record.kafkaPartition, updatedSchema, updatedValue, record.valueSchema, record.value, record.timestamp)
  }

  class Value[R <: ConnectRecord[R]] extends CSVStringFieldToArray[R] {
    override protected def operatingSchema(record: R): Schema = record.valueSchema

    override protected def operatingValue(record: R): Any = record.value

    override protected def newRecord(record: R, updatedSchema: Schema, updatedValue: Any): R =
      record.newRecord(record.topic, record.kafkaPartition, record.keySchema, record.key, updatedSchema, updatedValue, record.timestamp)
  }

}

abstract class CSVStringFieldToArray[R <: ConnectRecord[R]] extends Transformation[R] {
  private var fields: List[String] = _
  private var delimiter: String = _
  private var schemaUpdateCache: Cache[Schema, Schema] = _

  override def configure(props: util.Map[String, _]): Unit = {
    val config = new SimpleConfig(CSVStringFieldToArray.CONFIG_DEF, props)
    // nestedName = config.getString(NestingSPTypeFields.NESTED_NAME_CONFIG)
    delimiter = config.getString(CSVStringFieldToArray.DELIMITER_CONFIG)
    fields = config.getList(CSVStringFieldToArray.FIELDS_CONFIG)
    schemaUpdateCache = new SynchronizedCache[Schema, Schema](new LRUCache[Schema, Schema](16))
  }

  override def apply(record: R): R =
    if (operatingSchema(record) == null) record else applyWithSchema(record)

  private def applyWithSchema(record: R) = {
        val value = requireStruct(operatingValue(record), CSVStringFieldToArray.PURPOSE)
    var updatedSchema = schemaUpdateCache.get(value.schema)
    if (updatedSchema == null) {
      updatedSchema = makeUpdatedSchema(value.schema)
      schemaUpdateCache.put(value.schema, updatedSchema)
    }
    val updatedValue = new Struct(updatedSchema)
    
    for (field <- value.schema.fields.asScala) {
      if (isChangeable(field) && value.get(field) != null) {
        updatedValue.put(field.name, value.get(field).toString.split(delimiter).toList.asJava)
      } else if (isChangeable(field) && value.get(field) == null) {
        updatedValue.put(field.name, ArrayBuffer().asJava)
      } else updatedValue.put(field.name, value.get(field))
    }
    newRecord(record, updatedSchema, updatedValue)
  }

  private def makeUpdatedSchema(schema: Schema) = {
    val builder = SchemaUtil.copySchemaBasics(schema, SchemaBuilder.struct)

    // import scala.collection.JavaConversions._
    import scala.jdk.CollectionConverters._
    // import scala.jdk.javaapi.CollectionConverters._

    for (field <- schema.fields.asScala) {
      if (isChangeable(field)) builder.field(field.name,SchemaBuilder.array(Schema.STRING_SCHEMA))
      else builder.field(field.name, field.schema)
    }
    builder.build
  }

  private def isChangeable(field: Field) = fields.contains(field.name)

  override def close(): Unit = {
    schemaUpdateCache = null
  }

  override def config: ConfigDef = CSVStringFieldToArray.CONFIG_DEF

  protected def operatingSchema(record: R): Schema

  protected def operatingValue(record: R): Any

  protected def newRecord(record: R, updatedSchema: Schema, updatedValue: Any): R
}
