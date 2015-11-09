package com.actitracker.job;


import com.actitracker.data.DataManager;
import com.actitracker.data.ExtractFeature;
import com.datastax.spark.connector.japi.CassandraRow;
import com.datastax.spark.connector.japi.rdd.CassandraJavaRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.linalg.Vectors;
import org.apache.spark.mllib.tree.model.DecisionTreeModel;
import org.apache.spark.mllib.tree.model.RandomForestModel;

import java.util.UUID;

import static com.actitracker.data.ExtractFeature.computeAvgAbsDifference;
import static com.actitracker.data.ExtractFeature.computeResultantAcc;
import static com.datastax.spark.connector.japi.CassandraJavaUtil.javaFunctions;

public class PredictActivity {


  public static PredictActivityResult Predict(String userId, long startTimestamp, long finishTimestamp, JavaSparkContext sc) {
      PredictActivityResult result = new PredictActivityResult();
      result.Prediction = -1;

      try{
          //System.out.println("DecisionTree: " + predictDecisionTree(sc));
          result = predictRandomForest(sc, userId, startTimestamp, finishTimestamp);
          System.out.println("RandomForest: " + result.Prediction);

      } catch (Exception ex) {
          System.out.println("PredictActivity.Predict() - Error: " + ex.getMessage());
      }

      return result;
  }

  public static double predictDecisionTree(JavaSparkContext sc) {

    DecisionTreeModel model = DecisionTreeModel.load(sc.sc(), "actitracker");

    double[] feature = {3.3809183673469394,-6.880102040816324,0.8790816326530612,50.08965378708187,84.13105050494424,20.304453787081833,5.930491461890875,7.544194085797583,3.519248229904206,12.968485972481643,7.50031E8};
      //double[] feature = {0.3809183673469394,0.880102040816324,0.8790816326530612,0.08965378708187,0.13105050494424,0.304453787081833,0.930491461890875,0.544194085797583,0.519248229904206,0.968485972481643,0.50031E8};


    Vector sample = Vectors.dense(feature);
    double prediction = model.predict(sample);

    return prediction;

  }

    public static PredictActivityResult predictRandomForest(JavaSparkContext sc, String receivedUserId, long startTimestamp, long finishTimestamp) {

        RandomForestModel model = RandomForestModel.load(sc.sc(), "actitracker");

        //double[] feature = {3.3809183673469394,-6.880102040816324,0.8790816326530612,50.08965378708187,84.13105050494424,20.304453787081833,5.930491461890875,7.544194085797583,3.519248229904206,12.968485972481643,7.50031E8};
        //double[] feature = {0.3809183673469394,0.880102040816324,0.8790816326530612,0.08965378708187,0.13105050494424,0.304453787081833,0.930491461890875,0.544194085797583,0.519248229904206,0.968485972481643,0.50031E8};


        UUID userId = UUID.fromString(receivedUserId);

        //walking1
        //long startTimestamp = 1445808714;
        //long finishTimestamp = 1445809489;

        //walking2
        //long startTimestamp = 1446044906;
        //long finishTimestamp = 1446046209;

        //sitting
        //long startTimestamp = 1446045575;
        //long finishTimestamp = 1446045931;

        //standing
        //long startTimestamp = 1446045625;
        //long finishTimestamp = 1446046109;

        //standing2
        //long startTimestamp = 1446048341;
        //long finishTimestamp = 1446048892;


        double[] feature = getAccelerationsFeatures(userId, startTimestamp, finishTimestamp, sc);

        Vector sample = Vectors.dense(feature);
        double prediction = model.predict(sample);

        PredictActivityResult par = new PredictActivityResult();
        par.Features = feature;
        par.Prediction = prediction;

        return par;

    }

    public static double[] getAccelerationsFeatures(UUID userId, long startTimestamp, long finishTimestamp, JavaSparkContext sc) {

        double[] features = new double[]{};

        // retrieve data from Cassandra and create an CassandraRDD
        CassandraJavaRDD<CassandraRow> cassandraRowsRDD = javaFunctions(sc).cassandraTable("gait", "accelerations");
        //CassandraJavaRDD<CassandraRow> cassandraRowsRDD = javaFunctions(sc).cassandraTable("gait", "accelerations");

        JavaRDD<CassandraRow> data = cassandraRowsRDD.select("timestamp_long", "x", "y", "z")
                .where("user_id=? AND timestamp >= ? AND timestamp <= ?", userId, startTimestamp, finishTimestamp)
                .withAscOrder().cache();

        if (data.count() > 0) {
            // transform into double array
            JavaRDD<double[]> doubles = DataManager.toDoubleLive(data);
            // transform into vector without timestamp
            JavaRDD<Vector> vectors = doubles.map(Vectors::dense);
            // data with only timestamp and acc
            JavaRDD<long[]> timestamp = DataManager.withTimestampLive(data);

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

            features = new double[]{
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
        }

        return features;
    }
}
