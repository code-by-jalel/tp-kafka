# kafka-pos-pipeline

Pipeline de logs de caisses POS en temps réel avec Apache Kafka.

## Prérequis

| Outil | Version minimale |
|-------|-----------------|
| Java  | 11              |
| Maven | 3.8+            |
| Kafka | 3.x (mode KRaft ou ZooKeeper) |

---

## 1. Démarrage de Kafka (mono-broker)

### Option A — Mode KRaft (sans ZooKeeper, Kafka ≥ 3.3)

```bash
# Générer un UUID de cluster
KAFKA_CLUSTER_ID=$(kafka-storage.sh random-uuid)

# Formater le répertoire de logs
kafka-storage.sh format -t $KAFKA_CLUSTER_ID \
  -c $KAFKA_HOME/config/kraft/server.properties

# Démarrer le broker
kafka-server-start.sh $KAFKA_HOME/config/kraft/server.properties
```

### Option B — Mode ZooKeeper (classique)

```bash
# Terminal 1 — ZooKeeper
zookeeper-server-start.sh $KAFKA_HOME/config/zookeeper.properties

# Terminal 2 — Broker Kafka
kafka-server-start.sh $KAFKA_HOME/config/server.properties
```

---

## 2. Build du projet

```bash
git clone <url-du-repo>
cd kafka-pos-pipeline
mvn clean package -q
```

Le JAR exécutable est généré dans `target/kafka-pos-pipeline-1.0-SNAPSHOT.jar`.

---

## 3. Création des topics

```bash
java -cp target/kafka-pos-pipeline-1.0-SNAPSHOT.jar \
     com.pos.producer.TopicSetup
```

Ou manuellement :

```bash
kafka-topics.sh --create --bootstrap-server localhost:9092 \
  --topic pos-events --partitions 4 --replication-factor 1

kafka-topics.sh --create --bootstrap-server localhost:9092 \
  --topic alertes-retours --partitions 1 --replication-factor 1
```

Vérification :

```bash
kafka-topics.sh --list --bootstrap-server localhost:9092
```

---

## 4. Lancement des composants

Ouvrir un terminal par composant.

### Producteur — SimulateurCaisse

```bash
# Caisse 1
java -cp target/kafka-pos-pipeline-1.0-SNAPSHOT.jar \
     com.pos.producer.SimulateurCaisse CAISSE-TUNIS-01

# Caisse 2 (dans un 2e terminal — test étape 13)
java -cp target/kafka-pos-pipeline-1.0-SNAPSHOT.jar \
     com.pos.producer.SimulateurCaisse CAISSE-SOUSSE-02
```

### Consommateur — ChiffreAffairesParVille

```bash
# Instance 1
java -cp target/kafka-pos-pipeline-1.0-SNAPSHOT.jar \
     com.pos.consumer.ChiffreAffairesParVille

# Instance 2 & 3 (dans d'autres terminaux — test rebalance étape 14)
java -cp target/kafka-pos-pipeline-1.0-SNAPSHOT.jar \
     com.pos.consumer.ChiffreAffairesParVille
```

### Consommateur — DetecteurAnomalies

```bash
java -cp target/kafka-pos-pipeline-1.0-SNAPSHOT.jar \
     com.pos.consumer.DetecteurAnomalies
```

---

## 5. Tests et vérifications

### Observer le LAG du groupe ca-1 (étape 16)

```bash
# Toutes les 10 secondes
watch -n 10 kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group ca-1
```

### Observer la répartition des partitions

```bash
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group ca-1
```

### Lire les alertes en direct

```bash
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic alertes-retours --from-beginning
```

### Lire tous les événements POS bruts

```bash
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic pos-events --from-beginning \
  --property print.key=true --property key.separator=" | "
```

---

## 6. Structure du projet

```
kafka-pos-pipeline/
├── pom.xml
├── README.md
└── src/main/java/com/pos/
    ├── model/
    │   ├── EvenementPOS.java      # Modèle de données JSON
    │   └── KafkaConfig.java       # Configuration centralisée
    ├── producer/
    │   ├── SimulateurCaisse.java  # Producteur (VENTE/RETOUR/OUVERTURE)
    │   └── TopicSetup.java        # Utilitaire de création des topics
    └── consumer/
        ├── ChiffreAffairesParVille.java  # Groupe ca-1
        └── DetecteurAnomalies.java       # Groupe alerte-1
```

---

## 7. Comportement attendu lors des tests

| Test | Comportement observé |
|------|---------------------|
| 2 producteurs simultanés | Débit doublé, partitionnement par ville maintenu |
| 3 instances ChiffreAffaires | Rebalance → 4 partitions réparties (ex: 2+1+1) |
| Kill d'une instance | Rebalance automatique, messages non commités rejoués |
| LAG avec `--describe` | LAG ≈ 0 en fonctionnement normal, spike lors d'un rebalance |

---

## 8. Arrêt propre

`Ctrl+C` sur chaque processus déclenche le `ShutdownHook` → flush + close garantis.
