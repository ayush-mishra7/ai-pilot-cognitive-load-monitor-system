import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs';

/**
 * WebSocket service for real-time STOMP communication with the backend.
 *
 * Uses SockJS as the transport fallback and STOMP as the messaging protocol.
 * Provides auto-reconnection with exponential back-off, subscription management,
 * and a simple hook-friendly API for React components.
 *
 * Topics:
 *   /topic/session/{id}/state            — latest telemetry + cognitive + risk + recommendations
 *   /topic/session/{id}/cognitive-history — new cognitive history entry (append on client)
 *   /topic/session/{id}/risk-history      — new risk history entry (append on client)
 *   /topic/sessions                       — session list updates
 */

const WS_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080') + '/ws';

let stompClient = null;
let connected = false;
let connectPromise = null;

/** Active subscriptions: Map<topic, StompSubscription> */
const subscriptions = new Map();

/** Listeners: Map<topic, Set<callback>> */
const listeners = new Map();

/**
 * Ensure the STOMP client is connected.
 * Returns a promise that resolves when connected (or immediately if already connected).
 */
function ensureConnected() {
  if (connected && stompClient?.connected) return Promise.resolve();
  if (connectPromise) return connectPromise;

  connectPromise = new Promise((resolve, reject) => {
    stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 3000,        // 3s reconnect
      heartbeatIncoming: 10000,    // server heartbeat every 10s
      heartbeatOutgoing: 10000,    // client heartbeat every 10s
      debug: () => {},             // suppress STOMP debug logs in production
      onConnect: () => {
        connected = true;
        connectPromise = null;
        // Re-subscribe any existing listeners after reconnect
        resubscribeAll();
        resolve();
      },
      onStompError: (frame) => {
        console.error('[WebSocket] STOMP error:', frame.headers?.message);
        connected = false;
        connectPromise = null;
      },
      onWebSocketClose: () => {
        connected = false;
        connectPromise = null;
        subscriptions.clear(); // Stale subscriptions — will resubscribe on reconnect
      },
    });

    stompClient.activate();

    // Timeout after 10s
    setTimeout(() => {
      if (!connected) {
        connectPromise = null;
        reject(new Error('WebSocket connection timeout'));
      }
    }, 10000);
  });

  return connectPromise;
}

/**
 * Re-subscribes all listeners after a reconnect.
 * Called internally when the STOMP connection is re-established.
 */
function resubscribeAll() {
  for (const [topic, callbackSet] of listeners.entries()) {
    if (callbackSet.size > 0 && !subscriptions.has(topic)) {
      const sub = stompClient.subscribe(topic, (message) => {
        const body = JSON.parse(message.body);
        const cbs = listeners.get(topic);
        if (cbs) cbs.forEach((cb) => cb(body));
      });
      subscriptions.set(topic, sub);
    }
  }
}

/**
 * Subscribe to a STOMP topic.
 *
 * @param {string} topic   — STOMP destination, e.g. `/topic/session/{id}/state`
 * @param {function} callback — Called with parsed JSON body on each message
 * @returns {function} unsubscribe function
 */
export function subscribe(topic, callback) {
  // Register listener
  if (!listeners.has(topic)) listeners.set(topic, new Set());
  listeners.get(topic).add(callback);

  // If already connected and not subscribed to this topic, subscribe now
  if (connected && stompClient?.connected && !subscriptions.has(topic)) {
    const sub = stompClient.subscribe(topic, (message) => {
      const body = JSON.parse(message.body);
      const cbs = listeners.get(topic);
      if (cbs) cbs.forEach((cb) => cb(body));
    });
    subscriptions.set(topic, sub);
  } else {
    // Trigger connection if not started
    ensureConnected().catch(() => {});
  }

  // Return unsubscribe function
  return () => {
    const cbs = listeners.get(topic);
    if (cbs) {
      cbs.delete(callback);
      // If no more listeners for this topic, unsubscribe from STOMP
      if (cbs.size === 0) {
        listeners.delete(topic);
        const sub = subscriptions.get(topic);
        if (sub) {
          try { sub.unsubscribe(); } catch { /* ignore */ }
          subscriptions.delete(topic);
        }
      }
    }
  };
}

/**
 * Disconnect the STOMP client.
 * Called on app unmount if needed.
 */
export function disconnect() {
  if (stompClient) {
    try { stompClient.deactivate(); } catch { /* ignore */ }
    stompClient = null;
    connected = false;
    connectPromise = null;
    subscriptions.clear();
    listeners.clear();
  }
}

/**
 * Check if the WebSocket is currently connected.
 */
export function isConnected() {
  return connected && stompClient?.connected;
}

/**
 * Publish a STOMP message to a destination.
 * Ensures connection before sending.
 *
 * @param {string} destination — STOMP destination, e.g. `/app/chat/{sessionId}`
 * @param {object} body — JSON body to send
 */
export async function publish(destination, body) {
  try {
    await ensureConnected();
    if (stompClient?.connected) {
      stompClient.publish({
        destination,
        body: JSON.stringify(body),
      });
    }
  } catch (e) {
    console.warn('[WebSocket] Failed to publish:', e);
  }
}

export default { subscribe, disconnect, isConnected, publish };
