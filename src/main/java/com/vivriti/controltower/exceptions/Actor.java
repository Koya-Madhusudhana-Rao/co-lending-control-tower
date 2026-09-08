package com.vivriti.controltower.exceptions;

public record Actor(String actorId, Role role) {
    public Actor {
        if (actorId == null || actorId.isBlank() || role == null) {
            throw new IllegalArgumentException("Actor ID and role are required");
        }
    }
}