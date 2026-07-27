package com.rivers.batch.aspect;

public final class TargetIpHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TargetIpHolder() {}

    public static void set(String host) { HOLDER.set(host); }

    public static String get() { return HOLDER.get(); }

    public static void clear() { HOLDER.remove(); }
}
