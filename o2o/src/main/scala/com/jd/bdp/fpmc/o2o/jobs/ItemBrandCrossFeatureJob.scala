package com.jd.bdp.fpmc.o2o.jobs

import com.github.nscala_time.time.Imports._
import com.jd.bdp.fpmc.Job
import com.jd.bdp.fpmc.entity.result.{CrossFeaturesId, Feature, Features}
import com.jd.bdp.fpmc.o2o.Constants
import com.jd.bdp.fpmc.produce.offline.{SparkSql2Hbase, SparkSql2HbaseParams}
import com.jd.bdp.fpmc.storage.HbaseStorage
import Constants
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.Row
import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.{SparkConf, SparkContext}

/**
 * Created by zhengchen on 2015/9/2.
 */
object ItemBrandCrossFeatureJob extends Job[SparkContext] {

  val fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
  val YESTERDAY = (DateTime.now - 1.days).toString(fmt)
  val YESTERDAY_TIMESTAMP = (fmt.parseMillis(YESTERDAY) / 1000).toInt

  val user_brand_buy_sql =
    s"""
       select user_id,
              brand_id,
              sum(three_days),
              sum(seven_days)
       from
              adm.o2o_user_sku_features_tmp
       where
              dt='$YESTERDAY' and action='buy'
       group by
              user_id,
              brand_id
     """

  def user_brand_row_2_features(row: Row): Features = {
    val id = new CrossFeaturesId(row.getString(0), Constants.CROSS_FEATURES_BRAND_ID,
      row.getInt(1).toLong, YESTERDAY_TIMESTAMP, true)
    val result = new Features(id)
    result.addFeature(new Feature(Constants.VW_NAME_3_DAYS_BUY_BRAND, row.getInt(2).toString))
    result.addFeature(new Feature(Constants.VW_NAME_7_DAYS_BUY_BRAND, row.getInt(3).toString))
    result
  }

  def run(sc: SparkContext) {

    val sqlContext = new HiveContext(sc)

    val hbaseStorage = new HbaseStorage( Constants.HBASE_ZOOKEEPER_QUORUM,
      Constants.HBASE_ZOOKEEPER_ZNODE_PARENT, Constants.HBASE_USER_ITEM_CROSS_TABLE
      , Bytes.toBytes(Constants.HBASE_OFFLINE_FEATURE_FAMILY))

    val params = SparkSql2HbaseParams(user_brand_buy_sql, user_brand_row_2_features,
      hbaseStorage)

    val workflow = new SparkSql2Hbase(params)
    val featureRdd = workflow.data2feature(sqlContext)
    workflow.feature2storage(featureRdd)

  }

  def main(args: Array[String]) {
    val conf = new SparkConf()
    val sc = new SparkContext(conf)
    run(sc)
    sc.stop()
  }

}