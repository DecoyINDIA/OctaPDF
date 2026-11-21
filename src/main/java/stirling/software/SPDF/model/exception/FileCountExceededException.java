package stirling.software.SPDF.model.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(
        value = HttpStatus.BAD_REQUEST,
        reason = "Request exceeds maximum allowed file count")
public class FileCountExceededException extends IllegalArgumentException {
    public FileCountExceededException(String message) {
        super(message);
    }
}
