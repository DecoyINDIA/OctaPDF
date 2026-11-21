package stirling.software.SPDF.model.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(
        value = HttpStatus.PAYLOAD_TOO_LARGE,
        reason = "Document exceeds maximum allowed page count")
public class PageCountExceededException extends IllegalArgumentException {
    public PageCountExceededException(String message) {
        super(message);
    }
}
