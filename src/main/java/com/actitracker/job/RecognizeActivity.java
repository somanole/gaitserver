package com.actitracker.job;


import com.actitracker.data.DataManager;
import com.actitracker.data.ExtractFeature;
import com.actitracker.data.PrepareData;
import com.actitracker.model.RandomForests;
import com.datastax.spark.connector.japi.CassandraRow;
import com.datastax.spark.connector.japi.rdd.CassandraJavaRDD;
import com.utils.Constants;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.linalg.Vectors;
import org.apache.spark.mllib.regression.LabeledPoint;

import java.util.ArrayList;
import java.util.List;

import static com.actitracker.data.ExtractFeature.computeAvgAbsDifference;
import static com.actitracker.data.ExtractFeature.computeResultantAcc;
import static com.datastax.spark.connector.japi.CassandraJavaUtil.javaFunctions;
import static java.util.Arrays.asList;

public class RecognizeActivity {

  private static List<String> ACTIVITIES = asList("Standing", "Jogging", "Walking", "Sitting", "Upstairs", "Downstairs");

  public static void main(String[] args) {

    // define Spark context
    SparkConf sparkConf = new SparkConf()
                                  .setAppName("User's physical activity recognition")
                                  .set("spark.cassandra.connection.host", Constants.CASSANDRA_MASTER)
                                  .set("spark.executor.memory", "1g")
                                  .setMaster(Constants.SPARK_MASTER_NAME);

    JavaSparkContext sc = new JavaSparkContext(sparkConf);

    // retrieve data from Cassandra and create an CassandraRDD
    CassandraJavaRDD<CassandraRow> cassandraRowsRDD = javaFunctions(sc).cassandraTable("actitracker", "users");
    //CassandraJavaRDD<CassandraRow> cassandraRowsRDD = javaFunctions(sc).cassandraTable("gait", "accelerations");

    List<LabeledPoint> labeledPoints = new ArrayList<>();

    for (int k = 2; k < 3; k++) {

      int i =0;
      if (k == 0) {
          i = 17;
      } else if (k == 1) {
          i = 20;
      } else if (k ==2) {
        i = 33;
      }
      for (String activity: ACTIVITIES) {

        // create bucket of sorted data by ascending timestamp by (user, activity)
        JavaRDD<Long> times = cassandraRowsRDD.select("timestamp")
                                              .where("user_id=? AND activity=?", i, activity)
                                              .withAscOrder()
                                              .map(CassandraRow::toMap)
                                              .map(entry -> (long) entry.get("timestamp"))
                                              .cache();

        // if data (do we consider less than 100 records per activity no data?!)
        if (times.count() > 100) {

          //////////////////////////////////////////////////////////////////////////////
          // PREPARE THE DATA: define the windows for each activity records intervals //
          //////////////////////////////////////////////////////////////////////////////
          List<Long[]> intervals = defineWindows(times);

          for (Long[] interval: intervals) {
            for (int j = 0; j <= interval[2]; j++) {

              JavaRDD<CassandraRow> data = cassandraRowsRDD.select("timestamp", "acc_x", "acc_y", "acc_z")
                  .where("user_id=? AND activity=? AND timestamp < ? AND timestamp > ?", i, activity, interval[1] + j * 5000000000L, interval[1] + (j - 1) * 5000000000L)
                  .withAscOrder().cache();

              if (data.count() > 0) {
                // transform into double array
                JavaRDD<double[]> doubles = DataManager.toDouble(data);
                // transform into vector without timestamp
                JavaRDD<Vector> vectors = doubles.map(Vectors::dense);
                // data with only timestamp and acc
                JavaRDD<long[]> timestamp = DataManager.withTimestamp(data);

                ////////////////////////////////////////
                // extract features from this windows //
                ////////////////////////////////////////
                ExtractFeature extractFeature = new ExtractFeature(vectors);

                // the average acceleration
                double[] mean = extractFeature.computeAvgAcc();

                // the variance
                double[] variance = extractFeature.computeVariance();

                // the average absolute difference
                double[] avgAbsDiff = computeAvgAbsDifference(doubles, mean);

                // the average resultant acceleration
                double resultant = computeResultantAcc(doubles);

                // the average time between peaks
                double avgTimePeak = extractFeature.computeAvgTimeBetweenPeak(timestamp);

                // Let's build LabeledPoint, the structure used in MLlib to create and a predictive model
                LabeledPoint labeledPoint = getLabeledPoint(activity, mean, variance, avgAbsDiff, resultant, avgTimePeak);

                labeledPoints.add(labeledPoint);
              }
            }
          }
        }
      }
    }

    // ML part with the models: create model prediction and train data on it //
    if (labeledPoints.size() > 0) {

      // data ready to be used to build the model
      JavaRDD<LabeledPoint> data = sc.parallelize(labeledPoints);

      // Split data into 2 sets : training (60%) and test (40%).
      JavaRDD<LabeledPoint>[] splits = data.randomSplit(new double[]{0.8, 0.2});
      JavaRDD<LabeledPoint> trainingData = splits[0].cache();
      JavaRDD<LabeledPoint> testData = splits[1];

      // With DecisionTree
      //double errDT = new DecisionTrees(trainingData, testData).createModel(sc);

      // With Random Forest
      double errRF = new RandomForests(trainingData, testData).createModel(sc);

      System.out.println("sample size " + data.count());
      //System.out.println("Test Error Decision Tree: " + errDT);
      //System.out.println("Test Error Random Forest: " + errRF);

    }

  }

  private static List<Long[]>  defineWindows(JavaRDD<Long> times) {
    // first find jumps to define the continuous periods of data
    Long firstElement = times.first();
    Long lastElement = times.sortBy(time -> time, false, 1).first();

    // compute the difference between each timestamp
    JavaPairRDD<Long[], Long> tsBoundariesDiff = PrepareData.boudariesDiff(times, firstElement, lastElement);

    // define periods of recording
    // if the difference is greater than 100 000 000, it must be different periods of recording
    // ({min_boundary, max_boundary}, max_boundary - min_boundary > 100 000 000)
    JavaPairRDD<Long, Long> jumps = PrepareData.defineJump(tsBoundariesDiff);

    // Now define the intervals
    return PrepareData.defineInterval(jumps, firstElement, lastElement, 5000000000L);
  }

  /**
    * build the data set with label & features (11)
    * activity, mean_x, mean_y, mean_z, var_x, var_y, var_z, avg_abs_diff_x, avg_abs_diff_y, avg_abs_diff_z, res, peak_y
   */
  private static LabeledPoint getLabeledPoint(String activity, double[] mean, double[] variance, double[] avgAbsDiff, double resultant, double avgTimePeak) {
    // First the feature
    double[] features = new double[]{
        mean[0],
        mean[1],
        mean[2],
        variance[0],
        variance[1],
        variance[2],
        avgAbsDiff[0],
        avgAbsDiff[1],
        avgAbsDiff[2],
        resultant,
        avgTimePeak
    };

    // Now the label: by default 0 for Walking
    double label = 0;

    if ("Jogging".equals(activity)) {
      label = 1;
    } else if ("Standing".equals(activity)) {
      label = 2;
    } else if ("Sitting".equals(activity)) {
      label = 3;
    } else if ("Upstairs".equals(activity)) {
      label = 4;
    } else if ("Downstairs".equals(activity)) {
      label = 5;
    }

    return new LabeledPoint(label, Vectors.dense(features));
  }
}
