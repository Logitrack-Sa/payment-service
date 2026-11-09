package com.logitrack.payment.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentProperties.class)
public class HttpClients {
  private JdkClientHttpRequestFactory factory() {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(10));
    return factory;
  }

  @Bean
  @LoadBalanced
  RestClient.Builder shipmentRestClientBuilder() {
    return RestClient.builder().requestFactory(factory());
  }

  @Bean("shipmentHttp")
  RestClient shipmentClient(
      @Qualifier("shipmentRestClientBuilder") RestClient.Builder builder, PaymentProperties p) {
    return builder.baseUrl(p.shipmentUrl().toString()).build();
  }

  @Bean("mercadoPagoHttp")
  RestClient mercadoPagoClient(PaymentProperties p) {
    return RestClient.builder()
        .requestFactory(factory())
        .baseUrl(p.apiBaseUrl().toString())
        .build();
  }
}
