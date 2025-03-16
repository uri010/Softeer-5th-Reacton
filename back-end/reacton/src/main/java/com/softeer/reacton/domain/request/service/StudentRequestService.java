package com.softeer.reacton.domain.request.service;

import com.softeer.reacton.domain.request.dto.RequestSendRequest;
import com.softeer.reacton.domain.request.dto.RequestSseRequest;
import com.softeer.reacton.global.sse.SseMessageSender;
import com.softeer.reacton.global.sse.dto.SseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentRequestService {

    private final SseMessageSender sseMessageSender;
    private final RequestService requestService;

    public void sendRequest(Long courseId, RequestSendRequest requestSendRequest) {
        String content = requestSendRequest.getContent();
        log.info("[Send Request Start] courseId = {}, content = {}", courseId, content);

        requestService.incrementRequestCount(content, courseId);

        RequestSseRequest requestSseRequest = new RequestSseRequest(content);

        log.info("[SSE Message Sending] courseId = {}, content = {}", courseId, content);

        SseMessage<RequestSseRequest> sseMessage = new SseMessage<>("REQUEST", requestSseRequest);
        sseMessageSender.sendMessage(courseId.toString(), sseMessage);

        log.info("[Send Request Completed] courseId = {}, content = {}", courseId, content);
    }
}
