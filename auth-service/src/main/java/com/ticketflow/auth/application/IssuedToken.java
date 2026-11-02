package com.ticketflow.auth.application;

import java.time.Duration;

public record IssuedToken(String value, Duration expiresIn) {
}
