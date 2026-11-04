import SockJS from 'sockjs-client';
import StompModule from 'stompjs/lib/stomp.js';

const Stomp = StompModule.Stomp;

let stompClient = null;
let subscriptions = [];

export const connectWebSocket = (onMessageCallback, vendorId, token) => {
  const configuredUrl = import.meta.env.VITE_WS_URL;
  const socketUrl = configuredUrl || `${window.location.protocol}//${window.location.host}/ws`;
  const socket = new SockJS(socketUrl);
  stompClient = Stomp.over(socket);
  stompClient.reconnect_delay = 3000;

  stompClient.connect({ Authorization: `Bearer ${token}` }, () => {
    // Subscribe to vendor-specific topic
    if (vendorId) {
      const subscription = stompClient.subscribe(
        `/topic/vendor/${vendorId}`,
        (message) => {
          const orderData = JSON.parse(message.body);
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
