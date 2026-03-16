package com.jcpineda.filestore.events;

import com.jcpineda.filestore.security.JwtPrincipal;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/events")
public class EventStreamController {

    private final UserEventPublisher userEventPublisher;

    public EventStreamController(UserEventPublisher userEventPublisher) {
        this.userEventPublisher = userEventPublisher;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new IllegalStateException("Authenticated principal is required");
        }

        SseEmitter emitter = userEventPublisher.subscribe(principal.userId());
        emitter.send(SseEmitter.event().name("connected").data("ok"));
        return emitter;
    }
}
