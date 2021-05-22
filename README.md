# Go Contain

To have kebernetes environment on local machine which have `Kafka` and `camel-k` 
>To ebable developer to have the envirenment to deploy and test kafka integration with camel-ks


To automate the steps,

* download minikube
* install minikube
* install helm repository in minikube
* get helm for kafka
* get helm for camel-k
* install kafka and camel-k using helms

# Kafka Fundamental

## Topics, partitions and offsetss
`Topics`

> a particular stream of data. Similar to a table in a database (without all constraints). Can have many topics as you wish. Identified by name

> topics are split in partitions, each partition is ordered. Each message in a partition gets an incremental id, called `offset`


  ![kafka topics](kafka_topics.png)

* `offset` only has a meaning for a specific partition.
  > Eg. offset 3 in partition 0 does not represent the same data as offset 3 in partition 1

* Order is guaranteed only within a partition (not across partitions)
* Data is kept only for a limited time (default is one week)
* Once the data is written to a partition, it can't be changed (immutability)
* Data is assigned randomly to a partition unless a key is provided

_use case_

>A trucking company has a fleet of trucks, each truck reports its GPS position to `Kafka`. In `Kafka` there is a topic named `trucks_gps` that contains the position of all trucks. Each truck will send a message to Kafka every 20 seconds, each message will contain the truck ID and the truck position (latitude and longitute). It can have 10 (arbitrary) partitions for this topic

![Trucks](uc.png)

## Brokers

* A Kafka cluster is composed of multiple brokers (servers)
* Each broker is identified with its ID (integer)
* Each broker contains certain topic partitions
* After connecting to any broker (called bootstrap broker), you will be connected to the entire cluster
* A good number to get started is 3 brokers, but some big clusters have over 100 brokers
* In these examples we choose to number brokers starting at 100 (abritrary)

`Example`

Topic-A with 3 partitions and Topic-B with 2 partitions.
In case there are 2 brokers.
Possible partitions accross brokers are as below,

|Broker 101|Broker 102|Broker 103|
|--|--|--|
|Topic-A Partition 0|Topic-A Partition 2|Topic-A Partition 1|
|Topic-B Partition 1|Topic-B Partition 0||

## Topic replication factor
* Topics should have a replication factor > | (usually between 2 and 3)
* ThiS way if a broker down, another broker can serve the data
* Example, Topic-A with 2 partitions and replication factor of 2

|Broker 101|Broker 102|Broker 103|
|--|--|--|
|Topic-B Partition 1|Topic-B Partition 0||
||Topic-B Partition 1|Topic-B Partition 0|

## Concept of Leader for a Partition
* At any time only ONE broker can be a leader for a given partition
* Only that leader can receive and serve data for a partition
* The other brokers will synchronize the data
* Therefore each partition has one leader and multiple ISR (in-sync replica)

## Producers
* Poducers write data to topics (which is made of partitions)
* Producers automatically know to which broker and partition to write to
* In case of Broker failures, Producers will automatically recover
* Producers can choose to receive acknowledgement of data writes:
  > akcs=0: Producer won't wait for acknowledgement (possible data lost)

  > acks=l: Producer will wait for leader acknowledgement (limited data loss)

  > acks=all: Leader + replicas aknowledgement (no data loss)

  ## Producers: Message keys

  * Producer can choose to send a `key` with the message (string, number,etc ...)
  * If key=null, data is sent round robin (broker 101 then 102 then 103 ...)
  * If a key is sent, then all messages for that key will always go to the same partition
  * A key is basically sent if you need message ordering for a specific field (ex: truck_id)

  >Advanced: we get this guarantee thanks to key hashing, which depends on the number of partitions
## Consumer
* Consumers read data from a topic (identified by name)
* Consumers know which broker to read from
* In case of broker failures, consumers know how to recover
* Data is read in order `within each partitions`

![Consumer](cons.png)

## Consumer Groups

* Consumers read data in consumer groups
* Each consumer within a group reads from exclusive partitions
* If you have more consomers than partitions, some consumers will be inactive
> Consumers will automatically user GroupCoordinator and a ConsumerCoordinator to assign a consumers to a partition.

## Consumer Offsets

* Kafka stores the offsets at which a consumer group has beed reading
* The offsets committed live in a Kafka topic named `__consumer_offsets`
* When a consumer in a agroup has processed data received from Kafka, it should be committing the offsets
* If a consumer dies, it will be able to read back from where it left off. Thanks to the committed consumer offsets.

## Delivery semantics for consumers

* At most once:
> offset are committed as soon as the message received
> If the processing goes wrong, the message will be lost (it won't read again)
* At lease once
> Offsets are committed after the message is processed
> if the processing goes wrong, the message will be read again
> This can result in duplicate processing message. Make sure your processing is idempotemt
* Exactly once
> Can be achieved for Kafka -> Kafka

## Zookeeper

![ZK](zk.png)

* Zookeeper manages brokers (keep a list of them)
* Zookeeper helps in performing leader election for partitions
* Zookeeper sends notifications to Kafka in case of changes (e.g. new topic, broker dies broker comes up, delete topics, etc)
* **Kafka cannot work without Zookeeper**
* Zookeeper by design operates with an odd number of servers (3,5,7,...)
* Zookeeper has a leader (handle writes) the rest of the servers are followers (handle reads)
* Zookeeper does not store the consumer offsets with Kafka > v.0.10

## Kafka Guarantees

* Messages are appended to a topic-partition in the order they are sent
* Consumers read messages in the order stored in a topic partition
* With a replication factor N, producers and consumers can tolerate up to N-1 brokers being down
* This is why a replication factor of 3 is a good idea:
 > Allows for one broker to be taken down for maintenance
 > Allows for another broker to be taken down unexpectedly
* As long as the number of partitions remains constant for a topic (no new partitions), the same key always go to the same partition

## Roundup

![Trucks](kafka_arch.png)

## Hand-ons
`list topic`

```
kafka-topics --zookeeper 127.0.0.1:2181 --list

```

`zookeeper`

```shell
zookeeper-server-start zookeeper.properties
```

`kafka`

```
kafka-server-start server.properties
```

`kafka console producer`

```
kafka-console-producer --broker-list 127.0.0.0:9092 --topic first_topic
```

`kafka console consumer`

```shell
```
# Kafka rides Camel-k

![Camel-k](camel_kafka.png)


`Prerequisites`

`kafka` and camel-k in minikube installed

## camel-k as consumer client

```java
from("kafka:<topic_name>?brokers=<broker-host>:<port>&groupId=<consumer_group_id>&clientId=<client_id>&seekTo=beginning&valueDeserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer")
                .process(exchange -> {
                    //business logic
                    }
                });

```

## camel-k as producer client

```java
from("timer:java?period=60000")
        .setHeader(KafkaConstants.KEY, constant("Camel")) // Key of the message
        .setBody()
        .simple("Hello "+ UUID.randomUUID())
        .to("kafka:first_topic?brokers=kafka-broker:9092");
```



 








