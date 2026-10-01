package com.mobybank.harness.application;

/** The session or folder the caller asked for does not exist. The REST layer maps this to 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String kind, String id) {
        super(kind + " not found: " + id);
    }
}
