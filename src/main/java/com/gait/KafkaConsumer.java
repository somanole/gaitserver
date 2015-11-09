package com.gait;

/**
 * Created by sorinmanole on 29/10/2015.
 */

import com.actitracker.job.PredictActivity;
import com.actitracker.job.PredictActivityResult;
import com.sun.tools.javac.util.Convert;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import org.apache.spark.api.java.JavaSparkContext;

public class KafkaConsumer implements Runnable {
    private KafkaStream m_stream;
    private int m_threadNumber;
    private JavaSparkContext m_sc;

    public KafkaConsumer(KafkaStream a_stream, int a_threadNumber, JavaSparkContext a_sc) {
        m_threadNumber = a_threadNumber;
        m_stream = a_stream;
        m_sc = a_sc;
    }

    public void run() {
        ConsumerIterator<byte[], byte[]> it = m_stream.iterator();
        while (it.hasNext()) {
            String messageBody = new String(it.next().message());
            System.out.println("Thread " + m_threadNumber + ": " + messageBody);

            if (messageBody != null && !messageBody.isEmpty()) {
                String[] messageArray = null;
                messageArray = messageBody.split(",", -1);

                if (messageArray != null && messageArray.length > 2) {

                    System.out.println("UserId: " + messageArray[0]);
                    System.out.println("StartTimestamp: " + messageArray[1]);
                    System.out.println("FinishTimestamp: " + messageArray[2]);

                    String userId = messageArray[0];
                    long startTimestamp = Convert.string2long(messageArray[1], 10);
                    long finishTimestamp = Convert.string2long(messageArray[2], 10);

                    PredictActivityResult par = new PredictActivityResult();
                    par.Prediction = -1;

                    par = PredictActivity.Predict(userId, startTimestamp, finishTimestamp, m_sc);

                    System.out.println("KafkaConsumer activity: " + par.Prediction);

                    if (par.Prediction == 0 || par.Prediction == 1 || par.Prediction == 5) {
                        CassandraClient.IncreaseWalkingProgress(userId);
                        CassandraClient.SavePerfectNumber(userId, par.Features);
                        CassandraClient.SaveFinalPerfectNumber(userId, par.Features);
                    }
                }
            }
        }

        System.out.println("Shutting down Thread: " + m_threadNumber);
    }
}
