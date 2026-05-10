package com.orderbook.core.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonConverter {
    private static final Logger log = LoggerFactory.getLogger(JsonConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false);
        objectMapper.configure(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL, true);
        objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.registerModule(new SimpleModule()
                .addSerializer(Double.class, ToStringSerializer.instance)
                .addSerializer(BigDecimal.class, new ToStringSerializer() {
                    @Override
                    public void serialize(Object value, JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider provider) throws IOException {
                        gen.writeString(((BigDecimal) value).stripTrailingZeros().toPlainString());
                    }
                })
        );
    }

    private static <T> String to(T input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to convert [{}] to json", input, ex);
            return null;
        }
    }

    public static <T> String toJsonString(T input) {
        return to(input);
    }

    public static <T> T fromJsonString(String src, Class<T> clazz) {
        return from(src, clazz);
    }

    public static <T> T fromJsonByte(byte[] bytes, Class<T> clazz) {
        return from(bytes, clazz);
    }

    public static <T> T fromJsonStream(InputStream ins, Class<T> clazz) {
        return from(ins, clazz);
    }

    public static <T> byte[] toBytes(T input) {
        try {
            return objectMapper.writeValueAsBytes(input);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to write [{}] to bytes", input, ex);
            return null;
        }
    }

    public static <T> T from(String src, Class<T> clazz) {
        try {
            return (T) (src == null ? null : objectMapper.readValue(src, clazz));
        } catch (IOException ex) {
            log.warn("Failed to parse str to obj [{}] of type [{}]", new Object[]{src, clazz.getSimpleName()}, ex);
            return null;
        }
    }

    public static <T> T from(String src, TypeReference<T> typeReference) {
        try {
            return (T) (src == null ? null : objectMapper.readValue(src, typeReference));
        } catch (IOException ex) {
            log.warn("Failed to parse str to obj [{}]", src, ex);
            return null;
        }
    }

    public static <T> T from(byte[] bytes, Class<T> clazz) {
        try {
            return (T) (bytes == null ? null : objectMapper.readValue(bytes, clazz));
        } catch (IOException ex) {
            log.warn("Failed to parse bytes to obj of type [{}]", clazz.getSimpleName(), ex);
            return null;
        }
    }

    public static <T> T from(InputStream ins, Class<T> clazz) {
        try {
            return (T) (ins == null ? null : objectMapper.readValue(ins, clazz));
        } catch (IOException ex) {
            log.warn("Failed to parse bytes to obj of type [{}]", clazz.getSimpleName(), ex);
            return null;
        }
    }

    public static <T> T convertObject(Object o, Class<T> clazz) {
        return (T) objectMapper.convertValue(o, clazz);
    }

    public static <T> T map2Obj(Map<String, Object> objMap, Class<T> clazz) {
        return (T) objectMapper.convertValue(objMap, clazz);
    }

    public static <T> Map<String, Object> obj2Map(T obj) {
        return (Map) objectMapper.convertValue(obj, Map.class);
    }

    public static <T> Set<T> str2Set(T obj) {
        return (Set) objectMapper.convertValue(obj, Set.class);
    }

    public static <KEY, VAL> Map<KEY, VAL> mapFromJsonStr(String jsonStr, Class<KEY> keyClass, Class<VAL> valueClass) {
        try {
            return (Map) objectMapper.readValue(jsonStr, objectMapper.getTypeFactory().constructParametricType(HashMap.class, new Class[]{keyClass, valueClass}));
        } catch (IOException var4) {
            log.warn("Failed to parse json str to map", var4);
            return Collections.emptyMap();
        }
    }

    public static Map mapFromJsonBytes(byte[] bytes, Class<?> keyClass, Class<?> valueClass) {
        try {
            return (Map) objectMapper.readValue(bytes, objectMapper.getTypeFactory().constructParametricType(HashMap.class, new Class[]{keyClass, valueClass}));
        } catch (IOException var4) {
            log.warn("Failed to parse json bytes to map", var4);
            return Collections.emptyMap();
        }
    }

    public static Map mapFromJsonStream(InputStream ins, Class<?> keyClass, Class<?> valueClass) {
        try {
            return (Map) objectMapper.readValue(ins, objectMapper.getTypeFactory().constructParametricType(HashMap.class, new Class[]{keyClass, valueClass}));
        } catch (IOException var4) {
            log.warn("Failed to parse json stream to map", var4);
            return Collections.emptyMap();
        }
    }

    public static <T> List<T> listFromJsonStr(String src, Class<T> clazz) {
        return fromList(src, clazz);
    }

    public static <T> List<T> listFromJsonStream(InputStream ins, Class<T> clazz) throws IOException {
        return fromList(ins, clazz);
    }

    public static JsonNode toJsonNode(Object obj) {
        return obj == null ? null : objectMapper.valueToTree(obj);
    }

    public static String map2Json(Map<String, Object> obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException var2) {
            log.warn("Failed to write map obj [{}] as string", obj, var2);
            return null;
        }
    }

    private static <T> List<T> fromList(String src, Class<T> clazz) {
        try {
            return (List) objectMapper.readValue(src, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (IOException ex) {
            log.warn("Failed to parse list obj [{}]", src, ex);
            return null;
        }
    }

    private static <T> List<T> fromList(InputStream ins, Class<T> clazz) throws IOException {
        return (List) objectMapper.readValue(ins, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }
}