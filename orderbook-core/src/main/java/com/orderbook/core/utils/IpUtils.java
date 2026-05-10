package com.orderbook.core.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class IpUtils {

    public static final InetAddress LOCAL_IP = resolveLocalIp();

    private static InetAddress resolveLocalIp() {
        try {
            InetAddress candidate = null;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    if (iface.isLoopback() || !iface.isUp()) {
                        continue;
                    }
                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr.isLoopbackAddress()) {
                            continue;
                        }
                        if (addr instanceof java.net.Inet4Address) {
                            return addr;
                        }
                        if (candidate == null && !addr.isLinkLocalAddress()) {
                            candidate = addr;
                        }
                    }
                }
            }
            if (candidate != null) {
                return candidate;
            }
            return InetAddress.getLocalHost();
        } catch (UnknownHostException | SocketException e) {
            throw new RuntimeException("Failed to resolve local IP address", e);
        }
    }
}
