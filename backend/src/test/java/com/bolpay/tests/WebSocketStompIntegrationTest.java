package com.bolpay.tests;

import com.bolpay.service.AuthService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketStompIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ORIGIN = "http://localhost:5173";

    @Autowired
    private AuthService authService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @LocalServerPort
    private int port;

    @Test
    void signedJwtCanSubscribeToOwnTopicButNotAnotherVendorsTopic() throws Exception {
        long vendorId = 41001L;
        String jwt = authService.generateToken("9000000001", vendorId);
        try (SockJsStompConnection connection = openConnection()) {
            connection.awaitSockJsOpen();
            connection.sendStompFrame("CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer "
                    + jwt + "\n\n\u0000");
            assertEquals("CONNECTED", command(connection.awaitStompFrame("CONNECTED")));

            connection.sendStompFrame("SUBSCRIBE\nid:own-topic\ndestination:/topic/vendor/" + vendorId
                    + "\n\n\u0000");
            String ownMessage = null;
            long messageDeadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < messageDeadline && ownMessage == null) {
                messagingTemplate.convertAndSend("/topic/vendor/" + vendorId, "own-topic-probe");
                String frame = connection.pollStompFrame(250);
                if (frame == null) {
                    continue;
                }
                assertTrue(!"ERROR".equals(command(frame)), "own-topic subscription must not be rejected");
                if ("MESSAGE".equals(command(frame))) {
                    ownMessage = frame;
                }
            }
            assertNotNull(ownMessage, "own-topic subscription should receive a message");
            assertTrue(ownMessage.endsWith("own-topic-probe\u0000"), "own-topic messages should reach the subscriber");

            connection.sendStompFrame("SUBSCRIBE\nid:other-topic\ndestination:/topic/vendor/41002\n\n\u0000");
            assertEquals("ERROR", command(connection.awaitStompFrame("ERROR")),
                    "another vendor's topic must be denied");
        }
    }

    @Test
    void missingAndInvalidJwtAreDeniedDuringStompConnect() throws Exception {
        assertConnectDenied(null);
        assertConnectDenied("Bearer invalid-test-jwt");
    }

    private void assertConnectDenied(String authorization) throws Exception {
        try (SockJsStompConnection connection = openConnection()) {
            connection.awaitSockJsOpen();
            StringBuilder connect = new StringBuilder("CONNECT\naccept-version:1.2\nheart-beat:0,0\n");
            if (authorization != null) {
                connect.append("Authorization:").append(authorization).append('\n');
            }
            connect.append('\n').append('\u0000');
            connection.sendStompFrame(connect.toString());
            assertEquals("ERROR", command(connection.awaitStompFrame("ERROR")),
                    "CONNECT without a valid bearer JWT must be denied");
        }
    }

    private SockJsStompConnection openConnection() throws Exception {
        String session = UUID.randomUUID().toString().replace("-", "");
        URI uri = URI.create("ws://localhost:" + port + "/ws/000/" + session + "/websocket");
        SockJsStompConnection connection = new SockJsStompConnection();
        connection.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Origin", ORIGIN)
                .buildAsync(uri, connection)
                .get(5, TimeUnit.SECONDS);
        return connection;
    }

    private static String command(String frame) {
        return frame.split("\\n", 2)[0];
    }

    private static final class SockJsStompConnection implements WebSocket.Listener, AutoCloseable {
        private final BlockingQueue<String> stompFrames = new LinkedBlockingQueue<>();
        private final CompletableFuture<Void> sockJsOpened = new CompletableFuture<>();
        private final StringBuilder fragments = new StringBuilder();
        private volatile WebSocket socket;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public synchronized CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) {
                String packet = fragments.toString();
                fragments.setLength(0);
                if ("o".equals(packet)) {
                    sockJsOpened.complete(null);
                } else if (packet.startsWith("a")) {
                    try {
                        List<String> frames = JSON.readValue(packet.substring(1), new TypeReference<>() { });
                        stompFrames.addAll(frames);
                    } catch (Exception exception) {
                        stompFrames.offer("__INVALID_SOCKJS_FRAME__");
                    }
                } else if (packet.startsWith("c")) {
                    stompFrames.offer("__SOCKJS_CLOSED__" + packet);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            stompFrames.offer("__WEBSOCKET_CLOSED__");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            stompFrames.offer("__WEBSOCKET_ERROR__" + error.getClass().getSimpleName());
        }

        void awaitSockJsOpen() throws Exception {
            sockJsOpened.get(5, TimeUnit.SECONDS);
        }

        void sendStompFrame(String frame) throws Exception {
            // SockJS client frames are JSON arrays; the server prefixes its own frames with "a".
            String sockJsPacket = JSON.writeValueAsString(List.of(frame));
            socket.sendText(sockJsPacket, true).get(5, TimeUnit.SECONDS);
        }

        String awaitStompFrame(String expectedCommand) throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline) {
                String frame = pollStompFrame(250);
                if (frame == null) {
                    continue;
                }
                String frameCommand = command(frame);
                if (expectedCommand.equals(frameCommand) || frame.startsWith("__")) {
                    return frame;
                }
            }
            throw new AssertionError("Timed out waiting for STOMP " + expectedCommand + " frame");
        }

        String pollStompFrame(long timeoutMillis) throws InterruptedException {
            return stompFrames.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() throws Exception {
            if (socket != null && !socket.isOutputClosed()) {
                try {
                    socket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete").get(2, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignored) {
                    // The server may already have closed the socket after a denied STOMP frame.
                }
            }
        }
    }
}
