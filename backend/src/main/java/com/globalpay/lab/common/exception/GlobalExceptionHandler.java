package com.globalpay.lab.common.exception;

import com.globalpay.lab.common.api.Response;
import com.globalpay.lab.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Response<Void> handleBusinessException(
            BusinessException ex) {

        ResultCode result = ex.getResultCode();

        return Response.error(result);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Response<Void> handleValidationException(
            MethodArgumentNotValidException ex) {

        return Response.error(ResultCode.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Response<Void> handleInvalidRequestBody(
            HttpMessageNotReadableException ex) {

        return Response.error(ResultCode.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public Response<Void> handleUnexpectedException(
            Exception ex) {

        log.error("Unexpected server error", ex);

        return Response.error(ResultCode.INTERNAL_ERROR);
    }
}
