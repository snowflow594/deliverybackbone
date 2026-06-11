package com.estefano.deliverybackbone.realtime;

/** Canales Redis pub/sub (ver ARQUITECTURA.md §2.4). */
public final class RedisChannels {

    public static final String INVENTORY = "channel:inventory";
    public static final String COURIERS = "channel:couriers";   // Fase 3

    private RedisChannels() {
    }
}
