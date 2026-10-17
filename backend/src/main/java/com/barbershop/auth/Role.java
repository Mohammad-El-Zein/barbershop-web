package com.barbershop.auth;

/** Account roles, always scoped to one tenant. */
public enum Role {
    CUSTOMER,
    /** A barber's own login, linked to their staff profile. */
    BARBER,
    ADMIN
}
