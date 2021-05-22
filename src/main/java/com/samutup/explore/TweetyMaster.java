package com.samutup.explore;

import com.samutup.explore.settings.SettingLoader;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import java.util.concurrent.TimeUnit;

public class TweetyMaster {

  private static final Logger LOGGER = LoggerFactory.getLogger(TweetyMaster.class);

  static {
    InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
  }
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx(new VertxOptions().setMaxEventLoopExecuteTime(10).setBlockedThreadCheckIntervalUnit(
        TimeUnit.SECONDS));
    ConfigRetriever.create(vertx, SettingLoader.getConfigRetrieverOptions()).getConfig(event -> {
      if (event.succeeded()) {
        //deploy verticle
        deploy(vertx, event.result(), TweetyVerticle.class.getName());

      } else {
        //deploy verticle
      }
    });
  }

  private static void deploy(Vertx vertx, JsonObject config, String verticleName) {
    DeploymentOptions routerDeploymentOptions = new DeploymentOptions().setConfig(config);
    vertx.deployVerticle(verticleName, routerDeploymentOptions, result -> {
      if (result.succeeded()) {
        LOGGER.info("Successfully deploy the verticle");
      } else if (result.failed()) {
        result.cause().printStackTrace();
        LOGGER.error(result.cause());
        System.exit(-1);
      } else {
        LOGGER.info("result " + result);
      }

    });
  }
}
