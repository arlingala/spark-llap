/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hortonworks.spark.sql.hive.llap

import collection.JavaConverters._
import org.apache.hadoop.hive.llap.{Schema}
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector.Category
import org.apache.hadoop.hive.serde2.typeinfo._
import org.apache.spark.sql.Row

object RowConverter {

  def llapRowToSparkRow(llapRow: org.apache.hadoop.hive.llap.Row, schema: Schema): Row = {

    Row.fromSeq({
      var idx = 0
      schema.getColumns.asScala.map(colDesc => {
        val sparkValue = convertValue(llapRow.getValue(idx), colDesc.getTypeInfo)
        idx += 1
        sparkValue
      })
    })
  }

  private def convertValue(value: Any, colType: TypeInfo): Any = {
    colType.getCategory match {
      // The primitives should not require conversion
      case Category.PRIMITIVE => value
      case Category.LIST => value.asInstanceOf[java.util.List[Any]].asScala.map(
        listElement => convertValue(
            listElement,
            colType.asInstanceOf[ListTypeInfo].getListElementTypeInfo))
      case Category.MAP => {
        // Try LinkedHashMap to preserve order of elements - is that necessary?
        var convertedMap = scala.collection.mutable.LinkedHashMap.empty[Any, Any]
        value.asInstanceOf[java.util.Map[Any, Any]].asScala.foreach((tuple) =>
          convertedMap(convertValue(tuple._1, colType.asInstanceOf[MapTypeInfo].getMapKeyTypeInfo)) =
            convertValue(tuple._2, colType.asInstanceOf[MapTypeInfo].getMapValueTypeInfo))
        convertedMap
      }
      case Category.STRUCT =>
        // Struct value is just a list of values. Convert each value based on corresponding typeinfo
        Row.fromSeq(
          colType.asInstanceOf[StructTypeInfo].getAllStructFieldTypeInfos.asScala.zip(
            value.asInstanceOf[java.util.List[Any]].asScala).map({
              case (fieldType, fieldValue) => convertValue(fieldValue, fieldType)
            }))
      case _ => null
    }
  }
}
