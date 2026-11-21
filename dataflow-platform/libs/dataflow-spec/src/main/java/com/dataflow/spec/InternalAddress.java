package com.dataflow.spec;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class InternalAddress {

    private static final List<String> INTERNAL_SUFFIXES = List.of(".local", ".internal", ".svc", ".cluster.local");
    private static final int UNIQUE_LOCAL_MASK = 0xfe;
    private static final int UNIQUE_LOCAL = 0xfc;
    private static final int CARRIER_FIRST = 100;
    private static final int CARRIER_SECOND_MASK = 0xc0;
    private static final int CARRIER_SECOND = 0x40;
    private static final int OCTET = 0xff;

    private InternalAddress() {}

    public static boolean is(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        if (!name.contains(".") || INTERNAL_SUFFIXES.stream().anyMatch(name::endsWith)) {
            return true;
        }
        try {
            return Arrays.stream(InetAddress.getAllByName(name)).anyMatch(InternalAddress::internal);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean internal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || (address instanceof Inet6Address && (bytes[0] & UNIQUE_LOCAL_MASK) == UNIQUE_LOCAL)
                || (!(address instanceof Inet6Address)
                        && (bytes[0] & OCTET) == CARRIER_FIRST
                        && (bytes[1] & CARRIER_SECOND_MASK) == CARRIER_SECOND);
    }
}
