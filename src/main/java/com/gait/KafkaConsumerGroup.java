package com.gait;

/**
 * Created by sorinmanole on 29/10/2015.
 */

import com.utils.Constants;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class KafkaConsumerGroup {
    private final ConsumerConnector consumer;
    private final String topic;
    private  ExecutorService executor;

    public KafkaConsumerGroup(String a_zookeeper, String a_groupId, String a_topic) {
        consumer = kafka.consumer.Consumer.createJavaConsumerConnector(
                createConsumerConfig(a_zookeeper, a_groupId));
        this.topic = a_topic;
    }

    public void shutdown() {
        if (consumer != null) consumer.shutdown();
        if (executor != null) executor.shutdown();
        try {
            if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                System.out.println("Timed out waiting for consumer threads to shut down, exiting uncleanly");
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted during shutdown, exiting uncleanly");
        }
    }

    public void run(int a_numThreads, JavaSparkContext sc) {
        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(topic, new Integer(a_numThreads));
        Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumer.createMessageStreams(topicCountMap);
        List<KafkaStream<byte[], byte[]>> streams = consumerMap.get(topic);

        // now launch all the threads
        //
        executor = Executors.newFixedThreadPool(a_numThreads);

        // now create an object to consume the messages
        //
        int threadNumber = 0;
        for (final KafkaStream stream : streams) {
            executor.submit(new KafkaConsumer(stream, threadNumber, sc));
            threadNumber++;
        }
    }

    private static ConsumerConfig createConsumerConfig(String a_zookeeper, String a_groupId) {
        Properties props = new Properties();
        props.put("zookeeper.connect", a_zookeeper);
        props.put("group.id", a_groupId);
        props.put("zookeeper.session.timeout.ms", "400");
        props.put("zookeeper.sync.time.ms", "200");
        props.put("auto.commit.interval.ms", "1000");

        return new ConsumerConfig(props);
    }

    public static void main(String[] args) {
        String zooKeeper = Constants.ZOOKEEPER_MASTER;
        String groupId = "group1";
        String topic = "test";
        int threads = 4;

        SparkConf sparkConf = new SparkConf()
                .setAppName("User's physical activity recognition")
                .set("spark.cassandra.connection.host", Constants.CASSANDRA_MASTER)
                .setMaster(Constants.SPARK_MASTER_NAME);

        JavaSparkContext sc = new JavaSparkContext(sparkConf);

        KafkaConsumerGroup example = new KafkaConsumerGroup(zooKeeper, groupId, topic);
        example.run(threads, sc);

        //try {
        //    Thread.sleep(0);
        //} catch (InterruptedException ie) {

        //}

        //example.shutdown();

        while(true){

        }
    }
}

