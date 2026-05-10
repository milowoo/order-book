//package com.orderbook.core.config;
//
//import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.client.SimpleClientHttpRequestFactory;
//import org.springframework.http.converter.FormHttpMessageConverter;
//import org.springframework.web.client.RestTemplate;
//import java.util.List;
//
//@Configuration
//public class RestTemplateConfig {
//
//    @Bean
//    public RestTemplate restTemplate() {
//        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
//        requestFactory.setConnectTimeout(5000);
//        requestFactory.setReadTimeout(5000);
//        RestTemplate restTemplate = new RestTemplate(requestFactory);
//        restTemplate.setMessageConverters(List.of(new FastJsonHttpMessageConverter(), new FormHttpMessageConverter()));
//        return restTemplate;
//    }
//}