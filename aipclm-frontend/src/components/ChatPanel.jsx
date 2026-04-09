import { useState, useEffect, useRef, useCallback } from 'react';
import { useWebSocket } from '../hooks/useWebSocket';
import { publish } from '../services/websocket';

/**
 * Reusable real-time chat panel for ATC ↔ Flight communication.
 *
 * @param {string}   sessionId  — Flight session UUID
 * @param {string}   senderName — Display name, e.g. "ATC-TOWER" or "FLT-84A58039"
 * @param {function} onClose    — Called when user clicks the close button
 */
export default function ChatPanel({ sessionId, senderName, onClose }) {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const bottomRef = useRef(null);
  const msgIdCounter = useRef(0);

  // Subscribe to chat topic — only add messages from OTHER party
  useWebSocket(
    sessionId ? `/topic/session/${sessionId}/chat` : null,
    useCallback((msg) => {
      // Avoid duplicating own messages (already added optimistically)
      if (msg.sender === senderName) return;
      setMessages((prev) => [
        ...prev,
        { ...msg, _id: `ws-${Date.now()}-${Math.random()}` },
      ]);
    }, [senderName])
  );

  // Auto-scroll on new messages
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const send = () => {
    const text = input.trim();
    if (!text || !sessionId) return;

    const now = new Date();
    const localMsg = {
      sender: senderName,
      text,
      timestamp: now.toISOString(),
      _id: `local-${++msgIdCounter.current}`,
    };

    // Optimistic: show immediately in own chat
    setMessages((prev) => [...prev, localMsg]);
    setInput('');

    // Publish via STOMP so the other party receives it
    publish(`/app/chat/${sessionId}`, {
      sender: senderName,
      text,
    });
  };

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  const isMe = (sender) => sender === senderName;

  /** Format timestamp as "09 Apr, 00:04:12" */
  const fmtTime = (ts) => {
    if (!ts) return '';
    try {
      const d = new Date(ts);
      const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
      const dd = String(d.getDate()).padStart(2, '0');
      const mon = months[d.getMonth()];
      const hh = String(d.getHours()).padStart(2, '0');
      const mm = String(d.getMinutes()).padStart(2, '0');
      const ss = String(d.getSeconds()).padStart(2, '0');
      return `${dd} ${mon}, ${hh}:${mm}:${ss}`;
    } catch {
      return '';
    }
  };

  return (
    <div style={{
      display: 'flex', flexDirection: 'column',
      width: '100%', height: '100%',
      background: 'rgba(0,0,0,0.9)',
      border: '1px solid rgba(255,184,0,0.3)',
      borderRadius: '8px',
      overflow: 'hidden',
      fontFamily: "'Share Tech Mono', monospace",
    }}>
      {/* Header */}
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        padding: '0.5rem 0.75rem',
        background: 'rgba(255,184,0,0.08)',
        borderBottom: '1px solid rgba(255,184,0,0.2)',
        flexShrink: 0,
      }}>
        <span style={{ color: '#FFB800', fontSize: '0.7rem', letterSpacing: '0.1em' }}>
          ✦ ATC COMMS — {sessionId?.slice(0, 8).toUpperCase()}
        </span>
        {onClose && (
          <button
            onClick={onClose}
            style={{
              background: 'none', border: 'none', color: 'rgba(255,255,255,0.5)',
              cursor: 'pointer', fontSize: '1rem', padding: 0, lineHeight: 1,
            }}
          >✕</button>
        )}
      </div>

      {/* Messages */}
      <div style={{
        flex: 1, overflowY: 'auto', padding: '0.5rem 0.75rem',
        display: 'flex', flexDirection: 'column', gap: '0.5rem',
      }}>
        {messages.length === 0 && (
          <div style={{
            color: 'rgba(255,255,255,0.25)', textAlign: 'center',
            marginTop: '2rem', fontSize: '0.7rem', lineHeight: 1.6,
          }}>
            — COMMS CHANNEL OPEN —<br/>
            Type below to transmit.
          </div>
        )}
        {messages.map((m) => {
          const me = isMe(m.sender);
          return (
            <div key={m._id} style={{
              alignSelf: me ? 'flex-end' : 'flex-start',
              maxWidth: '82%',
            }}>
              {/* Sender label */}
              <div style={{
                fontSize: '0.55rem',
                color: me ? '#00C2FF' : '#FFB800',
                marginBottom: '2px',
                textAlign: me ? 'right' : 'left',
                letterSpacing: '0.05em',
              }}>
                {m.sender}
              </div>

              {/* Message bubble */}
              <div style={{
                padding: '0.35rem 0.6rem',
                background: me ? 'rgba(0,194,255,0.1)' : 'rgba(255,184,0,0.1)',
                border: `1px solid ${me ? 'rgba(0,194,255,0.25)' : 'rgba(255,184,0,0.25)'}`,
                borderRadius: me ? '8px 8px 2px 8px' : '8px 8px 8px 2px',
                color: '#E6F1FF',
                fontSize: '0.75rem',
                wordBreak: 'break-word',
                lineHeight: 1.4,
              }}>
                {m.text}
              </div>

              {/* Timestamp */}
              <div style={{
                fontSize: '0.45rem',
                color: 'rgba(255,255,255,0.25)',
                marginTop: '2px',
                textAlign: me ? 'right' : 'left',
                letterSpacing: '0.03em',
              }}>
                {fmtTime(m.timestamp)}
              </div>
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      {/* Input area */}
      <div style={{
        display: 'flex', gap: '0.4rem',
        padding: '0.5rem 0.75rem',
        borderTop: '1px solid rgba(255,184,0,0.15)',
        flexShrink: 0,
      }}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKey}
          placeholder="Type message…"
          autoFocus
          style={{
            flex: 1, padding: '0.4rem 0.6rem',
            background: 'rgba(255,255,255,0.05)',
            border: '1px solid rgba(255,184,0,0.2)',
            borderRadius: '4px',
            color: '#E6F1FF',
            fontFamily: "'Share Tech Mono', monospace",
            fontSize: '0.75rem',
            outline: 'none',
          }}
        />
        <button
          onClick={send}
          style={{
            padding: '0.4rem 0.8rem',
            background: 'rgba(255,184,0,0.15)',
            border: '1px solid rgba(255,184,0,0.4)',
            borderRadius: '4px',
            color: '#FFB800',
            fontFamily: "'Orbitron', sans-serif",
            fontSize: '0.6rem',
            cursor: 'pointer',
            letterSpacing: '0.05em',
          }}
        >
          SEND
        </button>
      </div>
    </div>
  );
}
