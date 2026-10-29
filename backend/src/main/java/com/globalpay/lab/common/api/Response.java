package com.globalpay.lab.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Response<T>(
        int code,
        String message,
        T data
) {

   public static<T> Response<T> success(T data) {
       return new Response<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
   }

    public static Response<Void> success() {
       return new Response<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static Response<Void> error(ResultCode resultCode) {
       return new Response<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static Response<Void> error(String message) {
        return new Response<>(500, message, null);
    }
}
