package com.vivriti.controltower.exceptions;

import java.time.LocalDateTime;

public record ExceptionStatusChange(ExceptionStatus status, LocalDateTime changedAt, String reason) {
}