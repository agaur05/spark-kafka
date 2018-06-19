import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.SparkConf
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{DataStreamWriter, Trigger}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}

/**
  * The goal of this exercise is to develop a structured streaming spark application
  * that reads particulate matter readings from Kafka,
  * extract values from the the readings and then calculate aggregate statistics.
  *
  * You will do so by implementing the three methods below:
  * - ingestKafkaTopic
  * - extractValues
  * - calculateTopPollutionEventsPerWeek
  *
  * You're done when all test cases pass.
  *
  * The data will be streaming from five sources:
  * - US Post in Beijing
  * - US Post in Chengdu
  * - US Post in Guangzhou
  * - US Post in Shanghai
  * - US Post in Shenyang
  *
  * The first reading is from 1/1/2010 at midnight,
  * the last reading is from 12/31/2015 at 11pm.
  *
  * The key for each kafka record is the name of the source.
  * The value for each kafka record is a CSV string with the following columns:
  * - year, numeric year, e.g. 2015
  * - month, numeric month, January is 1 etc.
  * - day, numeric day, the first of the month is 1 etc.
  * - hour, numeric 24h hour, 11pm is 23 etc.
  * - season, ignore
  * - PM_US Post, a double, the particulate matter reading at the US Post for the source,
  * - DEWP, a double, the dew point
  * - HUMI, a double, the humidity
  * - PRES, a double, the pressure
  * - TEMP, a double, the temperature
  * - cbwd, a double, combined wind direction
  * - Iws, a double, cumulated wind speed
  * - precipitation, a double, hourly precipication in mm
  * - Iprec, a double, cumulated precipication
  *
  * Example kafka record:
  * key: "BeijingPM20100101_20151231.csv"
  * value: "2010,1,1,0,4,null,-21.0,43.0,1021.0,-11.0,NW,1.79,0.0,0.0"
  *
  * Good luck!
  *
  */
object Main {

  def main(args: Array[String]): Unit = {
    // Load application configuration
    val config = ConfigFactory.load()

    // Configure Spark
    val sparkConf: SparkConf = new SparkConf()
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")

    // Create Spark context
    val spark: SparkSession = SparkSession.builder.config(sparkConf).getOrCreate()

    // Create DAG, start it and wait for it to stop
    val dag = createDAG(spark, config)
    val query = dag.trigger(Trigger.ProcessingTime("1 hour")).start()
    query.awaitTermination()
  }

  /**
    * Create Structured Streaming DAG
    *
    * @param spark the spark session
    * @param config the application configuration
    * @return the data stream writer
    */
  def createDAG(spark: SparkSession, config: Config): DataStreamWriter[Row] = {
    val kafkaData = ingestKafkaTopic(
      spark,
      config.getString("bootstrap"),
      config.getString("topic"),
      config.getString("startingOffsets"),
      config.getLong("maxOffsets")
    )
    val extractedValues = extractValues(kafkaData)
    val topEvents = calculateTopPollutionEventsPerWeek(extractedValues)
    writeEventsToHive(topEvents, config.getString("outputPath"))
  }

  /**
    * Write Top Weekly Events to Hive
    *
    * @param events the events dataframe
    * @return the data stream writer
    */
  def writeEventsToHive(events: DataFrame, outputPath: String): DataStreamWriter[Row] = {
    events.writeStream
      .outputMode("append")
      .format("parquet")
      .option("path", outputPath)
  }

  /**
    * Ingest Kafka Topic with PM data
    *
    * The method must create a streaming dataframe that:
    * - connects tp the specified kafka bootstrap servers
    * - subscribes from specified kafka topic
    * - reads from the specified topic offsets
    * - reads maximum number of offsets per trigger
    *
    * The output dataframe must have the following columns:
    * - key - String (the kafka topic key)
    * - value - String (the kafka topic value)
    *
    * N.B. You will have to convert the key and value to string
    *
    * @param spark the spark session
    * @param bootstrap a string with the kafka bootstrap servers
    * @param topic the kafka topic
    * @param startingOffsets the starting kafka topic offsets
    * @param maxOffsets maximum number of offsets per trigger
    * @return a streaming dataframe
    */
  def ingestKafkaTopic(spark: SparkSession, bootstrap: String, topic: String, startingOffsets: String, maxOffsets: Long): DataFrame = {
    spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", bootstrap)
      .option("startingOffsets", startingOffsets)
      .option("maxOffsetsPerTrigger", maxOffsets)
      .option("subscribe", topic)
      .load()
      .select(
        col("key").cast("string"),
        col("value").cast("string")
      )
  }

  /**
    * This method extracts data from the records coming from Kafka
    *
    * Input streaming dataframe:
    * - key - String (The source of the record)
    * - value - String (The comma-separated string of values)
    *
    * Resulting streaming dataframe:
    * - source - String (the record source)
    * - pm - Double (the US Post particulate matter reading)
    * - temp - Double (the temperature)
    * - timestamp - Timestamp (based on record time information)
    *
    *   The resulting dataframe must not contain null pm values.
    *
    * @param df the input dataframe
    * @return resulting dataframe
    */
  def extractValues(df: DataFrame): DataFrame = {
    df.withColumn("tmp", split(col("value"), ","))
      .select(
        col("key").as("source"),
        col("tmp").getItem(0).cast("integer").as("year"),
        col("tmp").getItem(1).cast("integer").as("month"),
        col("tmp").getItem(2).cast("integer").as("day"),
        col("tmp").getItem(3).cast("integer").as("hour"),
        col("tmp").getItem(5).cast("double").as("pm"),
        col("tmp").getItem(11).cast("double").as("temp")
      )
      .withColumn(
        "timestamp",
        unix_timestamp(
          format_string("%04d-%02d-%02d %02d:00:00", col("year"), col("month"), column("day"), column("hour"))
        ).cast("timestamp")
      )
      .drop("year", "month", "day", "hour")
      .filter(col("pm").isNotNull)
  }

  /**
    * This method calculates the top pollution event by week and source.
    * Data more than two weeks old should be discarded.
    *
    * Input streaming dataframe:
    * - source - String (the record source)
    * - pm - Double (the US Post particulate matter reading)
    * - temp - Double (the temperature)
    * - timestamp - Timestamp (based on record time information)
    *
    * The resulting streaming dataframe:
    * - source - String
    * - start_timestamp - Timestamp (start of week)
    * - end_timestamp - Timestamp (end of week)
    * - max_pm - Double - the maximum particulate matter reading during the week
    * - mean_temp - Double - the mean temperature during the week (Round this using round() function)
    *
    * @param df the input dataframe
    * @return the resulting dataframe
    */
  def calculateTopPollutionEventsPerWeek(df: DataFrame): DataFrame = {
    df.withWatermark("timestamp", "8 days")
      .groupBy(col("source"), window(col("timestamp"), "1 week", "1 week"))
      .agg(
        max("pm").as("max_pm"),
        round(mean("temp")).as("mean_temp")
      )
      .select(
        col("source"),
        col("window.start").as("start_timestamp"),
        col("window.end").as("end_timestamp"),
        col("max_pm"),
        col("mean_temp")
      )
  }

}
