package com.rr.secureprint.dto;

import java.time.Instant;
import java.util.UUID;

public record ReleaseRequest(
        String printerCode
) {}
