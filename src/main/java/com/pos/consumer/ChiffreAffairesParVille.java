package com.pos.consumer;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pos.model.EvenementPOS;
import com.pos.model.EvenementPOS.TypeEvenement;
import com.pos.model.KafkaConfig;

public class ChiffreAffairesParVille {

    private static final Logger log = LoggerFactory.getLogger(ChiffreAffairesParVille.class);
    private static final String GROUP_ID = "ca-1";

    private final Map<String, Double> caParVille = Collections.synchronizedMap(new TreeMap<>());

    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper mapper;
    private volatile boolean running = true;

    public ChiffreAffairesParVille() {
        this.consumer = new KafkaConsumer<>(KafkaConfig.consumerProperties(GROUP_ID));
        this.mapper   = new ObjectMapper().registerModule(new JavaTimeModule());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Arrêt du consommateur ChiffreAffaires...");
            running = false;
        }));
    }

    public void demarrer() {
        consumer.subscribe(Collections.singletonList(KafkaConfig.TOPIC_POS_EVENTS));
        log.info("ChiffreAffairesParVille démarré | groupe: {} | topic: {}",
                GROUP_ID, KafkaConfig.TOPIC_POS_EVENTS);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::afficherBilan, 5, 5, TimeUnit.SECONDS);

        try {
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

                if (records.isEmpty()) continue;

                for (ConsumerRecord<String, String> record : records) {
                    traiterRecord(record);
                }

                consumer.commitSync();
                log.debug("Batch de {} messages commité.", records.count());
            }
        } catch (Exception e) {
            log.error("Erreur dans la boucle de consommation", e);
        } finally {
            try { consumer.commitSync(); } catch (Exception ignored) {}
            consumer.close();
            scheduler.shutdown();
        }
    }

    private void traiterRecord(ConsumerRecord<String, String> record) {
        try {
            EvenementPOS evt = mapper.readValue(record.value(), EvenementPOS.class);
            String ville     = evt.getVille();
            Double montant   = evt.getMontant();

            if (montant == null) return;

            if (evt.getType() == TypeEvenement.VENTE) {
                caParVille.merge(ville, montant, Double::sum);
            } else if (evt.getType() == TypeEvenement.RETOUR) {
                caParVille.merge(ville, -montant, Double::sum);
            }

        } catch (Exception e) {
            log.warn("Impossible de parser le message [offset={}]: {}",
                    record.offset(), record.value(), e);
        }
    }

    private void afficherBilan() {
        if (caParVille.isEmpty()) {
            log.info("── Bilan CA ── (aucune donnée encore)");
            return;
        }
        log.info("════════════════════════════════════════");
        log.info("  CHIFFRE D'AFFAIRES PAR VILLE");
        log.info("════════════════════════════════════════");
        double total = 0.0;
        synchronized (caParVille) {
            for (Map.Entry<String, Double> entry : caParVille.entrySet()) {
                log.info("  {:10s} : {:>10.2f} DT", entry.getKey(), entry.getValue());
                total += entry.getValue();
            }
        }
        log.info("────────────────────────────────────────");
        log.info("  TOTAL        : {:>10.2f} DT", total);
        log.info("════════════════════════════════════════");
    }

    public static void main(String[] args) {
        new ChiffreAffairesParVille().demarrer();
    }
}
