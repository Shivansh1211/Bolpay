package com.bolpay.tests;

import com.bolpay.config.WebSocketSecurityConfig;
import com.bolpay.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WebSocketSecurityConfigTest {
    @Test
    void rejectsSubscriptionToAnotherVendorTopic() {
        AuthService authService = mock(AuthService.class);
        WebSocketSecurityConfig interceptor = new WebSocketSecurityConfig(authService);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/vendor/42");
        accessor.setUser(new UsernamePasswordAuthenticationToken("41", null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, mock(MessageChannel.class)));
    }
}
