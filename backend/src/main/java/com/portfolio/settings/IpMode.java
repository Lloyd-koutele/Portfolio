package com.portfolio.settings;

/**
 * How visitor IP addresses are displayed and stored.
 */
public enum IpMode
{
    /** Full address. Must be declared in the privacy notice. */
    FULL,
    /** Last IPv4 octet replaced, IPv6 truncated to /48 (default). */
    MASKED,
    /** Only a short pseudonym derived from the salted hash. */
    HASHED
}
