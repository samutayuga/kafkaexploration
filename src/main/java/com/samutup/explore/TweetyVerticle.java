package com.samutup.explore;

import com.samutup.explore.liveready.AppCheckHandler;
import com.samutup.explore.liveready.LifenessReadinessCheck;
import com.samutup.explore.liveready.ServerStartupListener;
import com.samutup.explore.liveready.ServerStartupListenerImpl;
import com.samutup.explore.settings.TweetySetting;
import com.samutup.explore.twitter.TweetListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaProducer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.kafka.clients.producer.ProducerConfig;

/**
 * handle the request
 */
public class TweetyVerticle extends AbstractVerticle {


  private static final Logger LOGGER = LoggerFactory.getLogger(TweetyVerticle.class);
  static Consumer<KafkaConsumerRecord<String, String>> recordConsumer = consumerRecord -> LOGGER
      .info(
          "processing key=" + consumerRecord.key() + " value=" + consumerRecord.value()
              + " partition=" + consumerRecord.partition()
              + " offset=" + consumerRecord.offset());

  static KafkaConsumer<String, String> kafkaConsumerBuilder(String bootstrap, String group,
      Vertx vertx) {
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", bootstrap);
    config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("group.id", group);
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "false");
    return KafkaConsumer.create(vertx, config);
  }

  static KafkaProducer<String, String> kafkaProducerBuilder(Vertx vertx, String boostrap) {
    Map<String, String> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, boostrap);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    config.put("acks", "1");

// use producer for interacting with Apache Kafka
    KafkaProducer<String, String> producer = KafkaProducer.create(vertx, config);
    return producer;
  }

  TweetListener tweetListener = TweetListener.init().connect();
  private Handler<RoutingContext> contextHandler = routingContext -> {
    if (HttpMethod.DELETE.equals(routingContext.request().method())) {
      this.tweetListener.stop();
      routingContext.response().setStatusCode(HttpResponseStatus.OK.code()).end();
    } else {
      routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
    }
  };

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    Router router = Router.router(vertx);
    try {
      TweetySetting tweetySetting = config().mapTo(TweetySetting.class);
      LOGGER.info("retrieve settings from yaml " + tweetySetting);
      int portNumber = tweetySetting.getPortNumber();
      //list all path
      ServerStartupListener serverStartupListenHandler = new ServerStartupListenerImpl(startPromise,
          portNumber, tweetySetting);
      // register readiness and liveness check
      //new AppCheckHandler[]{serverStartupListenHandler}
      LifenessReadinessCheck
          .registerReadinessCheck(router, new AppCheckHandler[]{serverStartupListenHandler});
      LifenessReadinessCheck.registerLivenessCheck(router, null);
      //call kafka
      String bootstrapServer = String
          .format("%s:%s", tweetySetting.getBrokerHost(),
              tweetySetting.getBrokerPort());
      KafkaConsumer<String, String> kafkaConsumer = kafkaConsumerBuilder(bootstrapServer, "dummy",
          vertx)
          .handler(recordConsumer::accept);
      KafkaProducer<String, String> kafkaProducer = kafkaProducerBuilder(vertx, bootstrapServer);
      // create server
      HttpServer server = vertx.createHttpServer();
      tweetListener.listen(kafkaProducer, tweetySetting.getTopicName());
      router.route().handler(BodyHandler.create()).blockingHandler(contextHandler);
      server.requestHandler(router).listen(portNumber, serverStartupListenHandler);
    } catch (Exception exception) {
      LOGGER.error("Unexpected error, config " + config(), exception);
    }
  }
}
