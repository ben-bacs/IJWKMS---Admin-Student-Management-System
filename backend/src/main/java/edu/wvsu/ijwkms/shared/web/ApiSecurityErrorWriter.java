package edu.wvsu.ijwkms.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ApiSecurityErrorWriter {

    private final JsonMapper jsonMapper;

    public ApiSecurityErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(code, message, MDC.get(CorrelationIdFilter.MDC_KEY), List.of());
        jsonMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(error));
    }
}
