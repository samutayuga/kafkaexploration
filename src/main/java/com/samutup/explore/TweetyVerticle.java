package com.samutup.explore;

import com.samutup.explore.liveready.AppCheckHandler;
import com.samutup.explore.liveready.LifenessReadinessCheck;
import com.samutup.explore.liveready.ServerStartupListener;
import com.samutup.explore.liveready.ServerStartupListenerImpl;
import com.samutup.explore.settings.TweetySetting;
import com.samutup.explore.twitter.TweetListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
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

  /*static KafkaConsumer<String, String> kafkaConsumerBuilder(String bootstrap, String group,
      Vertx vertx) {
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", bootstrap);
    config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("group.id", group);
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "false");
    return KafkaConsumer.create(vertx, config);
  }*/

  static KafkaProducer<String, String> kafkaProducerBuilder(Vertx vertx,
      TweetySetting tweetySetting) {
    Map<String, String> config = new HashMap<>();
    String bootstrapServer = String
        .format("%s:%s", tweetySetting.getBrokerHost(),
            tweetySetting.getBrokerPort());
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    //for safer producer
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    config.put(ProducerConfig.RETRIES_CONFIG, String.valueOf(Integer.MAX_VALUE));
    //retry delay
    config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG,
        String.valueOf(tweetySetting.getRetryBackoffMs()));
    //delivery timeout
    config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
        String.valueOf(tweetySetting.getDeliveryTimeOutMs()));
    config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
        String.valueOf(tweetySetting.getRequestPerConn()));

    //high throughput producer (at the expense of a bit of latency and CPU usage)
    config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, tweetySetting.getCompressionType().name());
    config.put(ProducerConfig.BATCH_SIZE_CONFIG,
        Integer.toString(tweetySetting.getBatchSizeKb() * 1024));
    config.put(ProducerConfig.LINGER_MS_CONFIG, String.valueOf(tweetySetting.getLingerMs()));

// use producer for interacting with Apache Kafka
    return KafkaProducer.create(vertx, config);
  }

  TweetListener tweetListener;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    Router router = Router.router(vertx);
    try {
      TweetySetting tweetySetting = config().mapTo(TweetySetting.class);
      LOGGER.info("retrieve settings from yaml " + tweetySetting);
      int portNumber = tweetySetting.getPort();
      //list all path
      ServerStartupListener serverStartupListenHandler = new ServerStartupListenerImpl(startPromise,
          portNumber, tweetySetting);
      // register readiness and liveness check
      //new AppCheckHandler[]{serverStartupListenHandler}
      LifenessReadinessCheck
          .registerReadinessCheck(router, new AppCheckHandler[]{serverStartupListenHandler});
      LifenessReadinessCheck.registerLivenessCheck(router, null);
      //call kafka

      // create server
      HttpServer server = vertx.createHttpServer();
      // tweetListener.listen(kafkaProducer, tweetySetting.getTopicName());
      router.route().handler(BodyHandler.create()).handler(rc -> {
        if (rc.request().uri() != null && (rc.request().uri()
            .startsWith(tweetySetting.getRestProduce()))) {
          if (HttpMethod.DELETE.equals(rc.request().method())) {
            this.tweetListener.stop();
            rc.response().setStatusCode(HttpResponseStatus.OK.code()).end();
          } else if (HttpMethod.POST.equals(rc.request().method())) {
            KafkaProducer<String, String> kafkaProducer = kafkaProducerBuilder(vertx,
                tweetySetting);
//            vertx.<Void>executeBlocking(
//                f -> this.tweetListener.receive(kafkaProducer,
//                    tweetySetting.getTopicName()), voidAsyncResult -> LOGGER.info("completed"));
            this.tweetListener = TweetListener.init().connect();
            this.tweetListener
                .listen(kafkaProducerBuilder(vertx, tweetySetting), tweetySetting.getTopicName());
            rc.response().setStatusCode(HttpResponseStatus.CREATED.code()).end();
          } else {
            rc.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
          }

        } else {
          rc.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
        }

      });
      server.requestHandler(router).listen(portNumber, serverStartupListenHandler);
    } catch (Exception exception) {
      LOGGER.error("Unexpected error, config " + config(), exception);
    }
  }
}
