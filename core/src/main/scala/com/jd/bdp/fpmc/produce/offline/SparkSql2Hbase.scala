package com.jd.bdp.fpmc.produce.offline

import com.jd.bdp.fpmc.entity.result.Features
import com.jd.bdp.fpmc.storage.HbaseStorage
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{SQLContext, Row}

/**
 * Created by zhengchen on 2015/9/2.
 */

case class SparkSql2HbaseParams(sparkSql: String,
                                row2features: Row => Features,
                                st: HbaseStorage) extends Serializable

//TODO: write hfile to hbase http://wuchong.me/blog/2015/04/06/spark-on-hbase-new-api/
class SparkSql2Hbase(val ss2hp: SparkSql2HbaseParams) extends BaseWorkFlow[SQLContext, RDD[Features], HbaseStorage] {

  @transient
  override def data2feature(sqlContext: SQLContext): RDD[Features] = {
    sqlContext.sql(ss2hp.sparkSql).map(ss2hp.row2features)
  }

  @transient
  override def feature2storage(fd: RDD[Features]) {
    fd.mapPartitions{ partitionOfRecords => {
      val hbaseTalbe = ss2hp.st.create
      partitionOfRecords.foreach { f =>
        hbaseTalbe.pushFeatures(f.getId, f)
      }
      Iterator.single()
    }
    }.count()
  }

  @transient
  def feature2storage4hbase(fd: RDD[Features]): Unit = {
    ss2hp.st
  }

}