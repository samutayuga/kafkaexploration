package com.samutup.explore.twitter;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.event.Event;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

public class TweetListener {

  static Logger LOGGER = LoggerFactory.getLogger(TweetListener.class);
  List<String> trends;
  Client client;
  BlockingQueue<String> msgQueue = new LinkedBlockingDeque<String>(100000);
  BlockingQueue<Event> eventBlockingQueue = new LinkedBlockingQueue<Event>(1000);
  static BiConsumer<RecordMetadata, JsonObject> biConsumer = (record, jsonObject) -> LOGGER.info(
      "pushed: " + jsonObject.get("id") + ",offset:" + record.getOffset() + ",partition:" + record
          .getPartition() + ",topic:" + record.getTopic());

  public static TweetListener init(List<String> trends) {
    return new TweetListener(trends);
  }

  private TweetListener(List<String> trends) {

    Hosts hosts = new HttpHosts(Constants.STREAM_HOST);
    StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();
    List<Long> followings = Lists.newArrayList(1234L, 566788L);
    List<String> terms = trends;
    hosebirdEndpoint.followings(followings);
    hosebirdEndpoint.trackTerms(terms);
    TweetCred tweetCred = new TweetCred();
    Authentication authentication = new OAuth1(tweetCred.getConsumerKey(),
        tweetCred.getConsumerSecret(), tweetCred.getAccessToken(), tweetCred.getAccessSecret());
    ClientBuilder clientBuilder = new ClientBuilder()
        .name("samutup-01")
        .hosts(hosts)
        .endpoint(hosebirdEndpoint)
        .authentication(authentication)
        .processor(new StringDelimitedProcessor(msgQueue))
        .eventMessageQueue(eventBlockingQueue);
    this.client = clientBuilder.build();
  }

  public TweetListener connect() {
    this.client.connect();
    return this;
  }


  public void listen(KafkaProducer<String, String> producer, String topicName) {
    new Thread(() -> {
      while (!this.client.isDone()) {
        try {
          String msg = this.msgQueue.take();
          handle(msg, producer, topicName);
        } catch (InterruptedException e) {
          LOGGER.error("error while taking message", e);
          e.printStackTrace();
        }
      }
    }).start();

  }

  public static void handle(String tweet, KafkaProducer<String, String> producer,
      String topicName) {
    //filter the tweet content
    //TweetPayload tweetPayload = Json.decodeValue(tweet, TweetPayload.class);
    JsonObject twObject = JsonParser.parseString(tweet).getAsJsonObject();
    KafkaProducerRecord<String, String> producerRecord = KafkaProducerRecord
        .create(topicName, tweet);
    producer.send(producerRecord)
        .onSuccess(recordMetadata -> biConsumer.accept(recordMetadata, twObject));
  }

  public TweetListener stop() {
    this.client.stop();
    return this;
  }
}
