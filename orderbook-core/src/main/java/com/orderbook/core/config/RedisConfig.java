//package com.orderbook.core.config;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import com.fasterxml.jackson.databind.DeserializationFeature;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.annotation.JsonAutoDetect;
//import com.fasterxml.jackson.annotation.PropertyAccessor;
//import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
//import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
//import java.time.Duration;
//import io.lettuce.core.cluster.ClusterClientOptions;
//import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
//import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
//import org.springframework.data.redis.serializer.StringRedisSerializer;
//import org.springframework.data.redis.serializer.RedisSerializer;
//
//@Configuration
//@Slf4j
//public class RedisConfig {
//
//    private long refreshPeriodSeconds = 10;
//    private long adaptiveRefreshTimeoutSeconds = 30;
//
//    @Bean
//    public LettuceClientConfigurationBuilderCustomizer customizer() {
//        return builder -> builder.useSsl().disablePeerVerification();
//    }
//
//    @Bean
//    public LettuceClientConfigurationBuilderCustomizer lettuceClientConfigurationBuilderCustomizer() {
//        ClusterTopologyRefreshOptions clusterTopologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
//                // 开启针对RefreshTrigger中所有类型的事件的触发器
//                .enableAllAdaptiveRefreshTriggers()
//                .adaptiveRefreshTriggersTimeout(Duration.ofSeconds(adaptiveRefreshTimeoutSeconds))
//                // 开启集群拓扑结构周期性刷新
//                .enablePeriodicRefresh(Duration.ofSeconds(refreshPeriodSeconds))
//                .build();
//
//        return builder -> {
//            builder.clientOptions(ClusterClientOptions.builder()
//                    .topologyRefreshOptions(clusterTopologyRefreshOptions)
//                    .validateClusterNodeMembership(false)
//                    .build());
//        };
//    }
//
//    @Bean
//    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
//        log.info("redisTemplate init. redisConnectionFactory={}", redisConnectionFactory);
//        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
//        redisTemplate.setConnectionFactory(redisConnectionFactory);
//
//        ObjectMapper objectMapper = new ObjectMapper();
//        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
//        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build();
//        objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);
//        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
//
//        RedisSerializer<Object> valueRedisSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
//        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
//
//        redisTemplate.setKeySerializer(stringRedisSerializer);
//        redisTemplate.setValueSerializer(valueRedisSerializer);
//        redisTemplate.setHashKeySerializer(stringRedisSerializer);
//        redisTemplate.setHashValueSerializer(valueRedisSerializer);
//        redisTemplate.afterPropertiesSet();
//
//        return redisTemplate;
//    }
//}