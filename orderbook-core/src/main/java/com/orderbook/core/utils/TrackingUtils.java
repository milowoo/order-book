package com.orderbook.core.utils;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.http.server.reactive.ServerHttpRequest;

public class TrackingUtils {
    public static final String SIMPLE_TEMPLATE = "%s:%s";
    public static final String X_AMZN_TRACE_ID = "x-amzn-trace-id";
    public static final String CF_RAY = "cf-ray";
    public static final String AWS_TRACE_ROOT = "Root=";
    private static final String LOG_TRACE_UUID_PREFIX = "(UUID=";
    private static final String LOG_TRACE_UUID_SUFFIX = ")-";

    public static void saveTraceId(String traceId) {
        MDC.put("traceId", traceId);
    }

    public static String createUUIDForTraceId() {
        String uuid = generateUUID();
        MDC.put("traceId", uuid);
        return uuid;
    }

    public static String getTraceIdFromRequest(HttpServletRequest request) {
        String envFlag = request.getHeader("x-gray-env");
        String traceId = request.getHeader("x-trace-id");
        String awsTrace = request.getHeader("x-amzn-trace-id");
        String ray = request.getHeader("cf-ray");

        if (isNull(traceId)) {
            traceId = LOG_TRACE_UUID_PREFIX + generateUUID() + LOG_TRACE_UUID_SUFFIX;
        }

        if (StringUtils.isNotBlank(envFlag) && !StringUtils.containsIgnoreCase(traceId, envFlag)) {
            traceId = String.format(SIMPLE_TEMPLATE, envFlag, traceId);
        }

        traceId = wrapAwsTraceIfNo(traceId, awsTrace);
        traceId = wrapRayTraceIfNo(traceId, ray);
        return traceId;
    }

    public static String getTraceIdFromReactiveRequest(ServerHttpRequest httpRequest) {
        String envFlag = httpRequest.getHeaders().getFirst("x-gray-env");
        String traceId = httpRequest.getHeaders().getFirst("x-trace-id");
        String awsTrace = httpRequest.getHeaders().getFirst("x-amzn-trace-id");
        String ray = httpRequest.getHeaders().getFirst("cf-ray");

        if (isNull(traceId)) {
            traceId = LOG_TRACE_UUID_PREFIX + generateUUID() + LOG_TRACE_UUID_SUFFIX;
        }

        if (StringUtils.isNotBlank(envFlag) && !StringUtils.containsIgnoreCase(traceId, envFlag)) {
            traceId = String.format(SIMPLE_TEMPLATE, envFlag, traceId);
        }

        traceId = wrapAwsTraceIfNo(traceId, awsTrace);
        traceId = wrapRayTraceIfNo(traceId, ray);
        return traceId;
    }

    public static String getCurrentTraceId() {
        return MDC.get("traceId");
    }

    public static void clearTraceId() {
        MDC.remove("traceId");
    }

    public static void saveUserId(String userId) {
        if (!StringUtils.isEmpty(userId)) {
            MDC.put("userId", userId);
        }
    }

    public static void saveUserId(Long userId) {
        if (userId != null && userId > 0L) {
            MDC.put("userId", String.valueOf(userId));
        }
    }

    public static void clearUserId() {
        MDC.remove("userId");
    }

    private static boolean isNull(String traceId) {
        return StringUtils.isBlank(traceId)
                || StringUtils.containsIgnoreCase(traceId, "Ignored")
                || StringUtils.containsIgnoreCase(traceId, "N/A");
    }

    public static String generateUUID() {
        String s = UUID.randomUUID().toString();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            builder.append(c == '-' ? "" : c);
        }
        return builder.toString();
    }

    private static String wrapAwsTraceIfNo(String traceId, String awsTrace) {
        return StringUtils.isNotBlank(traceId) && traceId.contains("AWS")
                ? traceId
                : traceId + getAwsTrace(awsTrace);
    }

    private static String wrapRayTraceIfNo(String traceId, String ray) {
        return !StringUtils.isEmpty(ray) && !StringUtils.isBlank(traceId) && !traceId.contains("RAY")
                ? traceId + "-(RAY:" + ray + ")"
                : traceId;
    }

    private static String getAwsTrace(String awsTrace) {
        String mark = "";
        if (StringUtils.isNotBlank(awsTrace)) {
            try {
                awsTrace = awsTrace.substring(awsTrace.indexOf("Root=") + 5);
            } catch (Exception var3) {
            }
            mark = "-(AWS=" + awsTrace + ")";
        }
        return mark;
    }
}