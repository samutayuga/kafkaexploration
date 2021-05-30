package com.samutup.explore.twitter;

import java.util.Base64;

public class TweetCred {

  private String consumerKey;
  private String consumerSecret;
  private String accessToken;
  private String accessSecret;


  public TweetCred() {
    this.consumerSecret = System.getenv("CONSUMER_SECRET");
    this.consumerKey = System.getenv("CONSUMER_KEY");
    this.accessToken = System.getenv("ACCESS_TOKEN");
    this.accessSecret = System.getenv("ACCESS_SECRET");
  }

  public String getConsumerKey() {
    return consumerKey;
  }

  public String getConsumerSecret() {
    return consumerSecret;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getAccessSecret() {
    return accessSecret;
  }
}
