package com.samutup.explore.twitter;

public enum TweetCred {
  cons_key(""),
  cons_secret(""),
  access_token(""),
  access_secret("");
  String value;

  TweetCred(String value) {
    this.value = value;
  }

}
