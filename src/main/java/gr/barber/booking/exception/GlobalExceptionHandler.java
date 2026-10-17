package gr.barber.booking.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every error answer is RFC 7807 problem details. Where the person can act on it, a "code" tells
 * the web app which (Greek) message to show; "detail" stays English, for developers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception);
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException exception) {
        return withCode(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage()), "INVALID_CREDENTIALS");
    }

    // The code lets the client show a "resend verification email" action.
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ProblemDetail handleEmailNotVerified(EmailNotVerifiedException exception) {
        return withCode(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage()), "EMAIL_NOT_VERIFIED");
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ProblemDetail handleWeakPassword(WeakPasswordException exception) {
        return withCode(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()), "WEAK_PASSWORD");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException exception) {
        return withCode(ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large (max 10 MB)"),
                "FILE_TOO_LARGE");
    }

    // A unique or overlap rule in the database caught a race the service checks could not
    // (e.g. two barbers picking the same link at once): the request conflicts with current data.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataConflict(DataIntegrityViolationException exception) {
        return withCode(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with existing data"),
                "CONFLICT");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> String.valueOf(error.getDefaultMessage()),
                        (first, second) -> first
                ));

        ProblemDetail problem = withCode(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed"),
                "VALIDATION_FAILED");
        problem.setProperty("errors", errors);
        return problem;
    }

    // Raised for constraints on list elements, e.g. a working-hours entry without endTime.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidation(HandlerMethodValidationException exception) {
        List<String> errors = exception.getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError
                        ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                        : String.valueOf(error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = withCode(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed"),
                "VALIDATION_FAILED");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, ApiException exception) {
        return withCode(ProblemDetail.forStatusAndDetail(status, exception.getMessage()), exception.getCode());
    }

    private static ProblemDetail withCode(ProblemDetail problem, String code) {
        if (code != null) {
            problem.setProperty("code", code);
        }
        return problem;
    }
}
