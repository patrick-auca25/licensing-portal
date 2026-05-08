package rw.bnr.licensing.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * ApiError — the ONLY error structure returned by the API.
 * No raw stack traces. No Spring internal messages.
 * Clean, human-readable, suitable for UI display.
 */
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ApiError {
    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;

    public static ApiError of(int status, String error, String message) {
        return ApiError.builder()
                .status(status)
                .error(error)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
