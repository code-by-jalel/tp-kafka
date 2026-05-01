package com.pos.producer;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pos.model.EvenementPOS;
import com.pos.model.EvenementPOS.TypeEvenement;
import com.pos.model.KafkaConfig;

public class SimulateurCaisse {

    private static final Logger log = LoggerFactory.getLogger(SimulateurCaisse.class);

    private static final List<String> VILLES = Arrays.asList(
            "Tunis", "Sousse", "Sfax", "Bizerte", "Gabès"
    );

    private static final List<String> PRODUITS = Arrays.asList(
            "pain", "lait", "fromage", "yaourt", "beurre",
            "œufs", "viande", "poulet", "légumes", "fruits",
            "jus", "eau", "café", "thé", "sucre", "farine"
    );

    private final String idCaisse;
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    private final Random random;
    private volatile boolean running = true;

    public SimulateurCaisse(String idCaisse) {
        this.idCaisse = idCaisse;
        this.producer = new KafkaProducer<>(KafkaConfig.producerProperties());
        this.random   = new Random();
        this.mapper   = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[{}] Arrêt du simulateur...", idCaisse);
            running = false;
            producer.flush();
            producer.close();
        }));
    }

    public void demarrer() throws Exception {
        log.info("[{}] Démarrage du simulateur → topic: {}", idCaisse, KafkaConfig.TOPIC_POS_EVENTS);

        while (running) {
            EvenementPOS evenement = genererEvenement();
            String json = mapper.writeValueAsString(evenement);

            ProducerRecord<String, String> record = new ProducerRecord<>(
                    KafkaConfig.TOPIC_POS_EVENTS,
                    evenement.getVille(),
                    json
            );

            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("[{}] Erreur d'envoi: {}", idCaisse, exception.getMessage());
                } else {
                    log.debug("[{}] Envoyé → partition={} offset={} | {}",
                            idCaisse, metadata.partition(), metadata.offset(), evenement);
                }
            });

            log.info("[{}] ► {}", idCaisse, evenement);

            long delai = 100 + random.nextInt(401);
            Thread.sleep(delai);
        }
    }

    private EvenementPOS genererEvenement() {
        String ville = VILLES.get(random.nextInt(VILLES.size()));
        int   tirage = random.nextInt(100); // 0-99

        TypeEvenement type;
        Double montant = null;
        List<String> produits = null;

        if (tirage < 70) {
            type     = TypeEvenement.VENTE;
            montant  = 5.0 + random.nextDouble() * 495.0; // 5 à 500 DT
            produits = genererProduits();
        } else if (tirage < 80) {
            type    = TypeEvenement.RETOUR;
            montant = 5.0 + random.nextDouble() * 495.0;  // 5 à 500 DT
        } else {
            type = TypeEvenement.OUVERTURE;
        }

        return new EvenementPOS(type, idCaisse, ville, Instant.now(), montant, produits);
    }

    private List<String> genererProduits() {
        int nb = 1 + random.nextInt(5);
        List<String> selection = new java.util.ArrayList<>();
        for (int i = 0; i < nb; i++) {
            selection.add(PRODUITS.get(random.nextInt(PRODUITS.size())));
        }
        return selection;
    }

    public static void main(String[] args) throws Exception {
        String idCaisse = args.length > 0 ? args[0] : "CAISSE-DEFAULT-01";
        new SimulateurCaisse(idCaisse).demarrer();
    }
}
