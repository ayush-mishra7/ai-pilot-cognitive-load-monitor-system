import { useEffect, useRef } from 'react';
import { subscribe } from '../services/websocket';

/**
 * React hook for subscribing to a STOMP WebSocket topic.
 *
 * Automatically subscribes on mount and unsubscribes on unmount.
 * If the topic changes, the old subscription is replaced.
 *
 * @param {string|null} topic    — STOMP destination, e.g. `/topic/session/{id}/state`.
 *                                  Pass null to skip subscription.
 * @param {function}    callback — Called with parsed JSON body on each message.
 *                                  MUST be stable (wrap in useCallback).
 */
export function useWebSocket(topic, callback) {
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  useEffect(() => {
    if (!topic) return;

    const unsub = subscribe(topic, (data) => {
      callbackRef.current(data);
    });

    return unsub;
  }, [topic]);
}

export default useWebSocket;
