package com.jituileet.tvmouse;

/* Lightweight process-local bridge. MouseService registers itself here. */
public final class ServiceRegistry {
    private static MouseService service;
    public static synchronized void set(MouseService s){service=s;}
    public static synchronized MouseService get(){return service;}
}
