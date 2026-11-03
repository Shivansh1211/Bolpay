import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

let stompClient = null;
let subscriptions = [];

export const connectWebSocket = (onMessageCallback, vendorId, token) => {
  const socket = new SockJS('http://localhost:8080/ws');
  stompClient = Stomp.over(socket);
  stompClient.reconnect_delay = 3000;

  stompClient.connect({ Authorization: `Bearer ${token}` }, () => {
    console.log('WebSocket connected');
    
    // Subscribe to vendor-specific topic
    if (vendorId) {
      const subscription = stompClient.subscribe(
        `/topic/vendor/${vendorId}`,
        (message) => {
          const orderData = JSON.parse(message.body);
          console.log('Received order update:', orderData);
          if (onMessageCallback) {
            onMessageCallback(orderData);
          }
        }
      );
      subscriptions.push(subscription);
    }
  }, (error) => {
    console.error('WebSocket connection error:', error);
  });
};

export const disconnectWebSocket = () => {
  if (stompClient && stompClient.connected) {
    subscriptions.forEach(sub => sub.unsubscribe());
    subscriptions = [];
    stompClient.disconnect(() => {
      console.log('WebSocket disconnected');
    });
  }
};

export const getWebSocketStatus = () => {
  return stompClient && stompClient.connected ? 'CONNECTED' : 'DISCONNECTED';
};
