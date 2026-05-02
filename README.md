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

## 2. Build du projet

```bash
git clone https://github.com/code-by-jalel/tp-kafka.git
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

