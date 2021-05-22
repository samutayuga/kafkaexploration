package com.samutup.explore.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TweetySetting {

  private int portNumber;
  private String topicName;
  private String brokerHost;
  private String brokerPort;

  public String getTopicName() {
    return topicName;
  }

  public void setTopicName(String topicName) {
    this.topicName = topicName;
  }

  public String getBrokerHost() {
    return brokerHost;
  }

  public void setBrokerHost(String brokerHost) {
    this.brokerHost = brokerHost;
  }

  public String getBrokerPort() {
    return brokerPort;
  }

  public void setBrokerPort(String brokerPort) {
    this.brokerPort = brokerPort;
  }

  public int getPortNumber() {
    return portNumber;
  }

  public void setPortNumber(int portNumber) {
    this.portNumber = portNumber;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TweetySetting tweetySetting = (TweetySetting) o;
    return portNumber == tweetySetting.portNumber && Objects
        .equal(topicName, tweetySetting.topicName) && Objects
        .equal(brokerHost, tweetySetting.brokerHost) && Objects
        .equal(brokerPort, tweetySetting.brokerPort);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(portNumber, topicName, brokerHost, brokerPort);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("portNumber", portNumber)
        .add("topicName", topicName)
        .add("brokerHost", brokerHost)
        .add("brokerPort", brokerPort)
        .toString();
  }
}
