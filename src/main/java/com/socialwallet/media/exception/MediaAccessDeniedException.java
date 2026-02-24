package com.socialwallet.media.exception;

public class MediaAccessDeniedException extends RuntimeException {
    public MediaAccessDeniedException(String message) {
        super(message);
    }
}
