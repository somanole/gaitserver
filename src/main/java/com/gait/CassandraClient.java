package com.gait;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.utils.Constants;

/**
 * Created by sorinmanole on 30/10/2015.
 */
public class CassandraClient {
    public static void IncreaseWalkingProgress(String userId) {
        Cluster cluster;
        Session session;

        try {
            // Connect to the cluster and keyspace "demo"
            cluster = Cluster.builder().addContactPoint(Constants.CASSANDRA_MASTER).build();
            session = cluster.connect("gait");

            int walkingProgress = 0;

            String sql = String.format("SELECT walking_progress FROM users_extra_info WHERE user_id=%s LIMIT 1", userId);
            System.out.println(sql);

            ResultSet results = session.execute(sql);
            for (Row row : results) {
                walkingProgress = row.getInt("walking_progress");
            }

            if (walkingProgress < 100) {
                if (walkingProgress >= 95) {
                    walkingProgress = 100;
                } else {
                    walkingProgress += 2;
                }

                long timestamp = System.currentTimeMillis();

                sql = String.format("INSERT INTO users_extra_info (user_id, walking_progress, timestamp) VALUES (%s, %s, %s)", userId, walkingProgress, timestamp);
                System.out.println(sql);

                session.execute(sql);
            }

            cluster.close();

        } catch (Exception ex)
        {}


    }

    public static void SaveFinalPerfectNumber(String userId, double[] features) {
        Cluster cluster;
        Session session;

        try {
            // Connect to the cluster and keyspace "demo"
            cluster = Cluster.builder().addContactPoint(Constants.CASSANDRA_MASTER).build();
            session = cluster.connect("gait");

            int walkingProgress = 0;

            String sql = String.format("SELECT walking_progress FROM users_extra_info WHERE user_id=%s LIMIT 1", userId);
            System.out.println(sql);

            ResultSet results = session.execute(sql);
            for (Row row : results) {
                walkingProgress = row.getInt("walking_progress");
            }

            if (walkingProgress > 90 && walkingProgress <= 100) {
                sql = String.format("SELECT perfect_number FROM perfect_numbers_by_user WHERE user_id=%s", userId);
                System.out.println(sql);

                int count = 0;
                double perfectNumber = 0;

                results = session.execute(sql);
                for (Row row : results) {
                    count++;
                    perfectNumber += row.getDouble("perfect_number");
                }

                if (count > 0) {
                    perfectNumber = perfectNumber / count;
                }

                long timestamp = System.currentTimeMillis();

                sql = String.format("insert into perfect_numbers (user_id, timestamp, timestamp_long, mean_x, mean_y, mean_z, variance_x, variance_y, variance_z, avg_abs_diff_x, avg_abs_diff_y, avg_abs_diff_z, resultant, avg_time_peak_y, perfect_number) VALUES (%s, %s, %s, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, %s)", userId, timestamp, timestamp, perfectNumber);
                System.out.println(sql);

                session.execute(sql);


                sql = String.format("INSERT INTO users_extra_info (user_id, walking_progress, timestamp) VALUES (%s, %s, %s)", userId, 100, timestamp);
                System.out.println(sql);

                session.execute(sql);
            }

            cluster.close();

        } catch (Exception ex)
        {}

    }

    public static void SavePerfectNumber(String userId, double[] features) {
        Cluster cluster;
        Session session;

        try {
            // Connect to the cluster and keyspace "demo"
            cluster = Cluster.builder().addContactPoint(Constants.CASSANDRA_MASTER).build();
            session = cluster.connect("gait");

            int walkingProgress = 0;

            String sql = String.format("SELECT walking_progress FROM users_extra_info WHERE user_id=%s LIMIT 1", userId);
            System.out.println(sql);

            ResultSet results = session.execute(sql);
            for (Row row : results) {
                walkingProgress = row.getInt("walking_progress");
            }

            if (walkingProgress < 100) {
                long timestamp = System.currentTimeMillis();

                double newPerfectNumber = 0;

                for (double feature : features) {
                    newPerfectNumber += feature;
                }

                newPerfectNumber = newPerfectNumber / 11;

                sql = String.format("INSERT INTO perfect_numbers_by_user (user_id, timestamp, perfect_number) VALUES (%s, %s, %s)", userId, timestamp, newPerfectNumber);
                System.out.println(sql);

                session.execute(sql);
            }

            cluster.close();

        } catch (Exception ex)
        {}

    }
}
