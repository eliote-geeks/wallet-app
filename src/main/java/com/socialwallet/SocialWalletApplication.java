package com.socialwallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling 
public class SocialWalletApplication {
  public static void main(String[] args) {
    SpringApplication.run(SocialWalletApplication.class, args);
  }
}