/*
 * This file is licensed under the Apache License, Version 2.0
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

package org.tresamigos.smv

import scala.math.floor

import org.apache.spark.sql.catalyst.expressions._
/*
import org.apache.spark.sql.functions._
import org.apache.spark.sql.GroupedData
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation
import org.apache.spark.sql.catalyst.dsl.plans._
import org.apache.spark.sql.catalyst.ScalaReflection
import scala.reflect.runtime.universe.{TypeTag, typeTag}
import org.apache.spark.sql.catalyst.dsl.expressions._
*/

/**
 * SmvGDO - SMV GroupedData Operator
 **/

abstract class SmvGDO extends Serializable{
  def inGroupKeys: Seq[String]
  
  /* inGroupIterator in SmvCDS should be independent of what aggregation function will be performed, so 
   * it has too be very general and as a result, may not be very efficiant */
  def inGroupIterator(smvSchema:SmvSchema): Iterable[Row] => Iterable[Row]
  
  def outSchema(inSchema: SmvSchema): SmvSchema 
}

/**
 * Compute the quantile bin number within a group in a given SchemaRDD.
 * The algorithm assumes there are three columns in the input.
 * (group_ids*, key_id, value).  The group_ids* are used to segment the input before
 * computing the quantiles.  The key_id is a unique id within the group.  it will just be
 * carried over into the output to help the caller to link the result back to the input.
 * And finally, the value column is the column that the quantile bins will be computed.
 * For now, the group and key columns must either be string or numeric and the value
 * column must be numeric (int, long, float, double).
 * The output will contain the 3 input columns plus value_total, value_rsum, and
 * value_quantile column with a value in the range 1 to num_bins.
 */
class SmvQuantile(valueCol: String, numBins: Int) extends SmvGDO {

  val inGroupKeys = Nil 
  
  def outSchema(inSchema: SmvSchema) = {
    val oldFields = inSchema.entries
    val newFields = List(
      DoubleSchemaEntry(valueCol + "_total"),
      DoubleSchemaEntry(valueCol + "_rsum"),
      IntegerSchemaEntry(valueCol + "_quantile"))
    new SmvSchema(oldFields ++ newFields)
  }
  
  /** bound bin number value to range [1,numBins] */
  private def binBound(binNum: Int) = {
    if (binNum < 1) 1 else if (binNum > numBins) numBins else binNum
  }
  
  /**
   * compute the quantile for a given group of rows (all rows are assumed to have the same group id)
   * Input: Array[Row(groupids*, keyid, value, value_double)]
   * Output: Array[Row(groupids*, keyid, value, value_total, value_rsum, value_quantile)]
   */
  def inGroupIterator(inSchema:SmvSchema): Iterable[Row] => Iterable[Row] = {
    val ordinal = inSchema.getIndices(valueCol)(0)
    val valueEntry = inSchema.findEntry(valueCol).get.asInstanceOf[NumericSchemaEntry]
    val getValueAsDouble: Row => Double = {r =>
      valueEntry.numeric.toDouble(r(ordinal).asInstanceOf[valueEntry.JvmType])
    }
    
    {it: Iterable[Row] =>
      val inGroup = it.toSeq
      val valueTotal = inGroup.map(r => getValueAsDouble(r)).sum
      val binSize = valueTotal / numBins
      var runSum: Double = 0.0
      inGroup.sortBy(r => getValueAsDouble(r)).map{r =>
        runSum = runSum + getValueAsDouble(r)
        val bin = binBound(floor(runSum / binSize).toInt + 1)
        val newValsDouble = Seq(valueTotal, runSum)
        val newValsInt = Seq(bin)
        new GenericRow(Array[Any](r.toSeq ++ newValsDouble ++ newValsInt: _*))
      }
    }
  }
}
