package com.pos.consumer;

import java.time.Duration;
import java.util.Collections;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pos.model.EvenementPOS;
import com.pos.model.EvenementPOS.TypeEvenement;
import com.pos.model.KafkaConfig;

public class DetecteurAnomalies {

    private static final Logger log = LoggerFactory.getLogger(DetecteurAnomalies.class);
    private static final String GROUP_ID       = "alerte-1";
    private static final double SEUIL_ANOMALIE = 200.0;

    private final KafkaConsumer<String, String> consumer;
    private final KafkaProducer<String, String> alerteProducer;
    private final ObjectMapper mapper;
    private volatile boolean running = true;
    private long compteurAlertes = 0;

    public DetecteurAnomalies() {
        this.consumer       = new KafkaConsumer<>(KafkaConfig.consumerProperties(GROUP_ID));
        this.alerteProducer = new KafkaProducer<>(KafkaConfig.producerProperties());
        this.mapper         = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Arrêt du détecteur d'anomalies... {} alertes émises.", compteurAlertes);
            running = false;
            alerteProducer.flush();
            alerteProducer.close();
        }));
    }

    public void demarrer() {
        consumer.subscribe(Collections.singletonList(KafkaConfig.TOPIC_POS_EVENTS));
        log.info("DetecteurAnomalies démarré | groupe: {} | seuil: {} DT | alerte→ topic: {}",
                GROUP_ID, SEUIL_ANOMALIE, KafkaConfig.TOPIC_ALERTES);

        try {
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

                if (records.isEmpty()) continue;

                for (ConsumerRecord<String, String> record : records) {
                    analyserRecord(record);
                }

                consumer.commitSync();
            }
        } catch (Exception e) {
            log.error("Erreur dans la boucle de détection", e);
        } finally {
            try { consumer.commitSync(); } catch (Exception ignored) {}
            consumer.close();
        }
    }

    private void analyserRecord(ConsumerRecord<String, String> record) {
        try {
            EvenementPOS evt = mapper.readValue(record.value(), EvenementPOS.class);

            if (evt.getType() != TypeEvenement.RETOUR) return;
            if (evt.getMontant() == null || evt.getMontant() <= SEUIL_ANOMALIE) return;

            Alerte alerte = new Alerte(evt);
            String alerteJson = mapper.writeValueAsString(alerte);

            ProducerRecord<String, String> alerteRecord = new ProducerRecord<>(
                    KafkaConfig.TOPIC_ALERTES,
                    evt.getIdCaisse(),
                    alerteJson
            );

            alerteProducer.send(alerteRecord, (meta, ex) -> {
                if (ex != null) {
                    log.error("Erreur envoi alerte: {}", ex.getMessage());
                }
            });

            compteurAlertes++;
            log.warn("🚨 ALERTE RETOUR ANORMAL #{} | Caisse: {} | Ville: {} | Montant: {:.2f} DT",
                    compteurAlertes, evt.getIdCaisse(), evt.getVille(), evt.getMontant());

        } catch (Exception e) {
            log.warn("Impossible d'analyser le message [offset={}]", record.offset(), e);
        }
    }

    public static class Alerte {
        public String  type        = "RETOUR_ANORMAL";
        public String  idCaisse;
        public String  ville;
        public Double  montant;
        public String  timestamp;
        public String  message;

        public Alerte(EvenementPOS evt) {
            this.idCaisse  = evt.getIdCaisse();
            this.ville     = evt.getVille();
            this.montant   = evt.getMontant();
            this.timestamp = evt.getTimestamp() != null ? evt.getTimestamp().toString() : null;
            this.message   = String.format(
                    "Retour anormal détecté : %.2f DT à la caisse %s (%s)",
                    montant, idCaisse, ville
            );
        }
    }

    public static void main(String[] args) {
        new DetecteurAnomalies().demarrer();
    }
}
