package com.pos.producer;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pos.model.KafkaConfig;

public class TopicSetup {

    private static final Logger log = LoggerFactory.getLogger(TopicSetup.class);

    public static void main(String[] args) throws Exception {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfig.BOOTSTRAP_SERVERS);

        try (AdminClient admin = AdminClient.create(adminProps)) {

            NewTopic posEvents = new NewTopic(
                    KafkaConfig.TOPIC_POS_EVENTS,
                    KafkaConfig.NB_PARTITIONS,
                    (short) KafkaConfig.REPLICATION_FACTOR
            );

            NewTopic alertes = new NewTopic(
                    KafkaConfig.TOPIC_ALERTES,
                    1,
                    (short) KafkaConfig.REPLICATION_FACTOR
            );

            try {
                admin.createTopics(Arrays.asList(posEvents, alertes)).all().get();
                log.info("✅ Topics créés avec succès :");
                log.info("   - {} ({} partitions)", KafkaConfig.TOPIC_POS_EVENTS, KafkaConfig.NB_PARTITIONS);
                log.info("   - {} (1 partition)", KafkaConfig.TOPIC_ALERTES);
            } catch (ExecutionException e) {
                if (e.getCause().getClass().getSimpleName().equals("TopicExistsException")) {
                    log.info("ℹ️  Les topics existent déjà.");
                } else {
                    throw e;
                }
            }

            log.info("\nDétail des topics :");
            admin.describeTopics(Arrays.asList(
                    KafkaConfig.TOPIC_POS_EVENTS,
                    KafkaConfig.TOPIC_ALERTES
            )).allTopicNames().get().forEach((name, desc) ->
                    log.info("  {} → {} partition(s)", name, desc.partitions().size())
            );
        }
    }
}
