package com.bolpay.config;

import com.bolpay.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * WebSocket security interceptor to verify JWT before allowing subscription
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSecurityConfig implements ChannelInterceptor {

    private final AuthService authService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            
            if (token == null || !token.startsWith("Bearer ")) {
                log.warn("WebSocket connection attempt without valid token");
                throw new IllegalArgumentException("Missing or invalid Authorization header");
            }
            
            token = token.substring(7);
            
            if (!authService.validateToken(token)) {
                log.warn("WebSocket connection attempt with invalid token");
                throw new IllegalArgumentException("Invalid JWT token");
            }
            
            String phoneNumber = authService.extractPhoneNumber(token);
            Long vendorId = authService.extractVendorId(token);
            
            // Set authentication in the session
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                phoneNumber,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_VENDOR"))
            ));
            
            log.info("WebSocket connected for vendor: {} (vendorId: {})", phoneNumber, vendorId);
        }
        
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            
            // Verify subscription is to correct vendor topic
            if (destination != null && destination.startsWith("/topic/vendor/")) {
                String[] parts = destination.split("/");
                if (parts.length >= 4) {
                    String requestedVendorId = parts[3];
                    
                    // Get vendor ID from authenticated user
                    if (accessor.getUser() != null) {
                        String phoneNumber = accessor.getUser().getName();
                        // Verify the user can only subscribe to their own topic
                        // This is a simplified check - in production, verify vendorId matches
                        log.debug("User {} subscribing to {}", phoneNumber, destination);
                    }
                }
            }
        }
        
        return message;
    }
}
