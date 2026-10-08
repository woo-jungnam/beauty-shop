import React, { useState, useRef, useEffect } from 'react';
import { Sparkles, X, Send, Bot, ShoppingBag, CalendarCheck } from 'lucide-react';
import { useCustomerCart } from '../../stores/customerCartStore';
import { apiClient } from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import { formatCurrency, resolveMediaUrl } from '../../../shared/utils/formatters';

const escapeHtml = (text) => {
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;',
  };
  return String(text ?? '').replace(/[&<>"']/g, (m) => map[m]);
};

export const parseMarkdown = (md) => {
  if (!md) return '';
  let html = escapeHtml(md);

  // Inline formatting: bold, italic, inline code, links
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
  html = html.replace(/\*(.*?)\*/g, '<em>$1</em>');
  html = html.replace(/`([^`]+)`/g, '<code class="chatbot-code">$1</code>');
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, label, url) =>
    /^https?:\/\/[^\s]+$/i.test(url)
      ? `<a href="${url}" target="_blank" rel="noopener noreferrer" class="chatbot-link">${label}</a>`
      : label);

  const lines = html.split('\n');
  let formatted = '';
  let inList = false;
  let listType = 'ul';
  let inTable = false;
  let tableHeaderDone = false;

  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim();

    if (!trimmed) {
      if (inList) {
        formatted += `</${listType}>`;
        inList = false;
      }
      if (inTable) {
        formatted += '</tbody></table></div>';
        inTable = false;
        tableHeaderDone = false;
      }
      continue;
    }

    // Markdown Table: lines starting and ending with '|'
    if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
      if (inList) {
        formatted += `</${listType}>`;
        inList = false;
      }

      if (/^\|(\s*[-:]+\s*\|)+$/.test(trimmed)) {
        tableHeaderDone = true;
        continue;
      }

      const cells = trimmed.slice(1, -1).split('|').map((c) => c.trim());

      if (!inTable) {
        inTable = true;
        tableHeaderDone = false;
        formatted += '<div class="chatbot-table-wrap"><table class="chatbot-table"><thead><tr>';
        cells.forEach((c) => {
          formatted += `<th>${c}</th>`;
        });
        formatted += '</tr></thead><tbody>';
      } else {
        formatted += '<tr>';
        cells.forEach((c) => {
          formatted += `<td>${c}</td>`;
        });
        formatted += '</tr>';
      }
      continue;
    } else if (inTable) {
      formatted += '</tbody></table></div>';
      inTable = false;
      tableHeaderDone = false;
    }

    // Horizontal Divider --- or ***
    if (/^(\-{3,}|\*{3,})$/.test(trimmed)) {
      if (inList) {
        formatted += `</${listType}>`;
        inList = false;
      }
      formatted += '<hr class="chatbot-hr" />';
      continue;
    }

    // Headings
    if (trimmed.startsWith('#### ')) {
      if (inList) {
        formatted += `</${listType}>`;
        inList = false;
      }
      formatted += `<h5 class="chatbot-heading-sm">${trimmed.substring(5)}</h5>`;
      continue;
    }
    if (trimmed.startsWith('### ')) {
      if (inList) {
        formatted += `</${listType}>`;
        inList = false;
      }
      formatted += `<h4 class="chatbot-heading">${trimmed.substring(4)}</h4>`;
      continue;
    }
    if (trimmed.startsWith('## ')) {
      if (inList) {
        formatted += `</${listType}>`;
        inList = false;
      }
      formatted += `<h3 class="chatbot-heading-lg">${trimmed.substring(3)}</h3>`;
      continue;
    }

    // Blockquote
    if (trimmed.startsWith('&gt; ')) {
      if (inList) {
        formatted += `</${listType}>`;
        inList = false;
      }
      formatted += `<blockquote class="chatbot-quote">${trimmed.substring(5)}</blockquote>`;
      continue;
    }

    // Bullet lists: - item or * item
    if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      if (!inList || listType !== 'ul') {
        if (inList) formatted += `</${listType}>`;
        formatted += '<ul class="chatbot-list">';
        inList = true;
        listType = 'ul';
      }
      formatted += `<li>${trimmed.substring(2)}</li>`;
      continue;
    }

    // Numbered lists: 1. item
    const olMatch = trimmed.match(/^(\d+)\.\s+(.*)$/);
    if (olMatch) {
      if (!inList || listType !== 'ol') {
        if (inList) formatted += `</${listType}>`;
        formatted += '<ol class="chatbot-list">';
        inList = true;
        listType = 'ol';
      }
      formatted += `<li>${olMatch[2]}</li>`;
      continue;
    }

    if (inList) {
      formatted += `</${listType}>`;
      inList = false;
    }

    formatted += `<p class="chatbot-p">${trimmed}</p>`;
  }

  if (inList) {
    formatted += `</${listType}>`;
  }
  if (inTable) {
    formatted += '</tbody></table></div>';
  }

  return formatted;
};

export const ChatbotSkincareWidget = ({ onNavigate }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [aiConnected, setAiConnected] = useState(null);
  const [messages, setMessages] = useState([
    {
      id: 1,
      sender: 'bot',
      text: 'Chào bạn! Tôi là Trợ Lý Da Liễu AI của BeautyShop. Tôi có thể giúp bạn phân tích thành phần INCI, tư vấn routine trị mụn hoặc chọn dịch vụ Spa phù hợp. Bạn đang quan tâm đến vấn đề gì?'
    }
  ]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [chatSessionId] = useState(() => {
    try {
      let id = localStorage.getItem('beautyshop_chat_session_id');
      if (!id) {
        id = 'chat_' + Math.random().toString(36).substring(2, 10) + '_' + Date.now();
        localStorage.setItem('beautyshop_chat_session_id', id);
      }
      return id;
    } catch {
      return 'chat_' + Date.now();
    }
  });
  const messagesEndRef = useRef(null);
  const requestInFlight = useRef(false);

  const { addItem } = useCustomerCart();

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isTyping]);

  useEffect(() => {
    if (!isOpen) return;
    const checkConnection = async () => {
      try {
        const res = await apiClient.get(ENDPOINTS.CHATBOT.HEALTH, { timeout: 4000 });
        const data = res?.data || res;
        setAiConnected(data?.status === 'ok' || data?.status === 'UP');
      } catch {
        setAiConnected(false);
      }
    };
    checkConnection();
  }, [isOpen]);

  const quickPrompts = [
    'Da dầu mụn nên dùng B5 hay Niacinamide?',
    'Gợi ý routine phục hồi màng ẩm ban đêm',
    'Dịch vụ Spa Aqua Peel có đau không?'
  ];

  const handleSend = async (textToSend) => {
    const query = textToSend || input;
    if (!query.trim() || requestInFlight.current) return;
    requestInFlight.current = true;

    const userMsg = { id: Date.now(), sender: 'user', text: query };
    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setIsTyping(true);

    try {
      // 1. Send query to backend AI RAG Chatbot API
      const res = await apiClient.post(ENDPOINTS.CHATBOT.CHAT, {
        sessionId: chatSessionId,
        message: query.trim(),
      }, { timeout: 125000 });
      const data = res?.data || res;
      const botResponse = data?.answer || data?.message || '';
      const rawProducts = Array.isArray(data?.products) ? data.products : [];
      const recommendedProducts = rawProducts.map((p) => ({
        id: String(p.id ?? '').replace(/^(mysql_|service_)/, ''),
        name: p.name,
        brand: p.brand || 'BeautyShop',
        price: p.price || 0,
        image: p.imageUrl || p.image_url || '',
        reason: p.reasonForRecommendation || p.reason_for_recommendation || '',
        targetType: p.targetType || p.target_type || 'PRODUCT',
      }));

      setAiConnected(true);
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          sender: 'bot',
          text: botResponse || 'Tôi đã tiếp nhận câu hỏi của bạn. Bạn có muốn tư vấn thêm về sản phẩm cụ thể không?',
          products: recommendedProducts.length > 0 ? recommendedProducts : undefined,
          product: recommendedProducts[0] || null,
        }
      ]);
    } catch {
      setAiConnected(false);
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          sender: 'bot',
          text: 'Trợ lý AI hiện chưa kết nối được. Vui lòng gửi lại câu hỏi sau ít phút.',
        }
      ]);
    } finally {
      requestInFlight.current = false;
      setIsTyping(false);
    }
  };

  return (
    <>
      {/* Floating Toggle Button */}
      <div className="chatbot-floating-toggle" style={{
        position: 'fixed',
        bottom: '24px',
        right: '24px',
        zIndex: 900
      }}>
        {!isOpen && (
          <button
            onClick={() => setIsOpen(true)}
            className="pulse-glow chatbot-toggle-button"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              padding: '12px 18px',
              borderRadius: '9999px',
              backgroundColor: 'var(--c-primary, #D45D79)',
              color: '#FFFFFF',
              border: '1.5px solid rgba(255, 255, 255, 0.4)',
              boxShadow: '0 8px 24px rgba(212, 93, 121, 0.35)',
              cursor: 'pointer',
              fontWeight: 700,
              fontSize: '13px'
            }}
          >
            <Sparkles size={18} color="#FFCCD5" />
            <span>Hỏi Bác Sĩ & AI Da Liễu</span>
          </button>
        )}
      </div>

      {/* Chat Popup Panel */}
      {isOpen && (
        <div className="chatbot-panel" style={{
          position: 'fixed',
          bottom: '24px',
          right: '24px',
          width: '420px',
          maxWidth: 'calc(100vw - 32px)',
          height: '560px',
          maxHeight: 'calc(100vh - 100px)',
          backgroundColor: '#FFFFFF',
          borderRadius: 'var(--radius-lg, 16px)',
          border: '1px solid var(--c-border)',
          boxShadow: '0 20px 50px rgba(0, 0, 0, 0.18)',
          display: 'flex',
          flexDirection: 'column',
          zIndex: 1000,
          overflow: 'hidden'
        }}>
          {/* Header */}
          <div style={{
            backgroundColor: 'var(--c-primary)',
            color: '#FFFFFF',
            padding: '14px 18px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <div style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                backgroundColor: 'rgba(255, 255, 255, 0.2)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                position: 'relative',
              }}>
                <Bot size={18} color="#FFFFFF" />
                <span
                  title={aiConnected === true ? 'AI trực tuyến' : aiConnected === false ? 'Trợ lý AI chưa kết nối được' : 'Đang kiểm tra kết nối...'}
                  style={{
                    position: 'absolute',
                    bottom: 0,
                    right: 0,
                    width: '8px',
                    height: '8px',
                    borderRadius: '50%',
                    backgroundColor: aiConnected === true ? '#22C55E' : aiConnected === false ? '#EAB308' : '#94A3B8',
                    border: '1.5px solid #fff',
                  }}
                />
              </div>
              <div>
                <div style={{ fontSize: '13px', fontWeight: 700 }}>BeautyShop Skincare AI</div>
                <div style={{ fontSize: '10px', color: aiConnected ? '#86EFAC' : '#D8C7C0' }}>
                  {aiConnected === true ? '● Trực tuyến' : aiConnected === false ? '○ Tạm thời gián đoạn' : 'Đang kết nối...'}
                </div>
              </div>
            </div>

            <button
              onClick={() => setIsOpen(false)}
              style={{
                background: 'transparent',
                border: 'none',
                color: '#EADFD9',
                cursor: 'pointer',
                padding: '4px'
              }}
            >
              <X size={18} />
            </button>
          </div>

          {/* Messages Body */}
          <div style={{
            flex: 1,
            overflowY: 'auto',
            padding: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
            backgroundColor: 'var(--c-canvas)'
          }}>
            {messages.map((msg) => (
              <div
                key={msg.id}
                style={{
                  display: 'flex',
                  justifyContent: msg.sender === 'user' ? 'flex-end' : 'flex-start',
                  gap: '8px'
                }}
              >
                {msg.sender === 'bot' && (
                  <div style={{
                    width: '28px',
                    height: '28px',
                    borderRadius: '50%',
                    backgroundColor: 'var(--c-primary)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0
                  }}>
                    <Bot size={14} color="var(--c-gold)" />
                  </div>
                )}

                <div
                  className={`chatbot-bubble ${msg.sender === 'user' ? 'chatbot-bubble-user' : 'chatbot-bubble-bot'}`}
                  style={{
                    maxWidth: msg.sender === 'user' ? '80%' : '88%',
                    padding: '11px 14px',
                    borderRadius: msg.sender === 'user' ? '14px 14px 4px 14px' : '4px 14px 14px 14px',
                    fontSize: '13px',
                    lineHeight: 1.6,
                    backgroundColor: msg.sender === 'user' ? 'var(--c-primary)' : '#FFFFFF',
                    color: msg.sender === 'user' ? '#FFFFFF' : 'var(--c-text-main)',
                    border: msg.sender === 'user' ? 'none' : '1px solid var(--c-border)',
                    boxShadow: '0 2px 6px rgba(0,0,0,0.03)',
                    wordBreak: 'break-word',
                  }}
                >
                  {msg.sender === 'bot' ? (
                    <div
                      className="chatbot-content"
                      dangerouslySetInnerHTML={{ __html: parseMarkdown(msg.text) }}
                    />
                  ) : (
                    <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.5 }}>
                      {msg.text}
                    </div>
                  )}

                  {/* Optional Recommended Product Cards inside chat */}
                  {(() => {
                    const recItems = (msg.products && msg.products.length > 0) ? msg.products : (msg.product ? [msg.product] : []);
                    if (recItems.length === 0) return null;
                    return (
                      <div style={{ marginTop: '12px', paddingTop: '10px', borderTop: '1px dashed var(--c-border)' }}>
                        <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--c-primary)', textTransform: 'uppercase', marginBottom: '8px', letterSpacing: '0.4px' }}>
                          Gợi ý phù hợp cho bạn ({recItems.length}):
                        </div>
                        {recItems.map((item, idx) => (
                          <div
                            key={idx}
                            style={{
                              marginBottom: idx === recItems.length - 1 ? 0 : '8px',
                              padding: '10px 12px',
                              backgroundColor: 'var(--c-gold-light)',
                              borderRadius: '10px',
                              border: '1px solid var(--c-gold-border)',
                            }}
                          >
                            <div style={{ display: 'flex', gap: '8px' }}>
                              {item.image ? (
                                <img
                                  src={resolveMediaUrl(item.image)}
                                  alt={item.name}
                                  style={{ width: '48px', height: '48px', objectFit: 'cover', borderRadius: '4px', flexShrink: 0 }}
                                  onError={(e) => { e.currentTarget.style.display = 'none'; }}
                                />
                              ) : null}
                              <div style={{ flex: 1, minWidth: 0 }}>
                                <div style={{ fontSize: '10px', color: 'var(--c-text-gold)', fontWeight: 700, textTransform: 'uppercase' }}>
                                  {item.targetType === 'SERVICE' ? 'LIỆU TRÌNH SPA PHÙ HỢP' : 'SẢN PHẨM KHUYÊN DÙNG'}
                                </div>
                                <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--c-primary)', margin: '2px 0 2px 0', lineHeight: 1.3 }}>
                                  {item.name}
                                </div>
                                {item.price > 0 && (
                                  <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--c-primary)' }}>
                                    {formatCurrency(item.price)}
                                  </div>
                                )}
                              </div>
                            </div>

                            {item.reason && (
                              <div style={{ fontSize: '11px', color: 'var(--c-text-muted)', marginTop: '4px', fontStyle: 'italic', lineHeight: 1.4 }}>
                                "{item.reason}"
                              </div>
                            )}

                            {item.targetType === 'SERVICE' ? (
                              <button
                                onClick={() => {
                                  setIsOpen(false);
                                  const route = `booking?serviceId=${encodeURIComponent(item.id)}`;
                                  if (typeof onNavigate === 'function') onNavigate(route);
                                  else window.location.hash = `/${route}`;
                                }}
                                style={{
                                  width: '100%',
                                  marginTop: '8px',
                                  padding: '6px',
                                  borderRadius: 'var(--radius-pill)',
                                  backgroundColor: 'var(--c-primary)',
                                  color: '#FFFFFF',
                                  border: 'none',
                                  fontSize: '11px',
                                  fontWeight: 700,
                                  cursor: 'pointer',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  gap: '4px',
                                }}
                              >
                                <CalendarCheck size={12} /> Đặt Lịch Liệu Trình Này
                              </button>
                            ) : (
                              <button
                                onClick={() => {
                                  addItem(item, null, 1);
                                }}
                                style={{
                                  width: '100%',
                                  marginTop: '8px',
                                  padding: '6px',
                                  borderRadius: 'var(--radius-pill)',
                                  backgroundColor: 'var(--c-gold)',
                                  color: '#FFFFFF',
                                  border: 'none',
                                  fontSize: '11px',
                                  fontWeight: 700,
                                  cursor: 'pointer',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  gap: '4px',
                                }}
                              >
                                <ShoppingBag size={12} /> Thêm Vào Giỏ Ngay
                              </button>
                            )}
                          </div>
                        ))}
                      </div>
                    );
                  })()}
                </div>
              </div>
            ))}

            {isTyping && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px', color: '#94A3B8' }}>
                <Bot size={14} color="var(--c-gold)" />
                <span>Bác sĩ AI đang phân tích dữ liệu thành phần...</span>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick Prompts */}
          <div style={{
            padding: '8px 12px',
            backgroundColor: '#FFFFFF',
            borderTop: '1px solid var(--c-border-subtle)',
            display: 'flex',
            gap: '6px',
            overflowX: 'auto'
          }}>
            {quickPrompts.map((p, i) => (
              <button
                key={i}
                disabled={isTyping}
                onClick={() => handleSend(p)}
                style={{
                  padding: '4px 10px',
                  borderRadius: 'var(--radius-pill)',
                  backgroundColor: 'var(--c-canvas-subtle)',
                  border: '1px solid var(--c-border)',
                  fontSize: '11px',
                  color: 'var(--c-text-muted)',
                  cursor: 'pointer',
                  whiteSpace: 'nowrap'
                }}
              >
                {p}
              </button>
            ))}
          </div>

          {/* Input Footer */}
          <div style={{
            padding: '12px',
            backgroundColor: '#FFFFFF',
            borderTop: '1px solid var(--c-border-subtle)',
            display: 'flex',
            gap: '8px'
          }}>
            <input
              type="text"
              placeholder="Nhập câu hỏi về da, thành phần..."
              disabled={isTyping}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSend()}
              style={{
                flex: 1,
                padding: '9px 12px',
                borderRadius: 'var(--radius-pill)',
                border: '1px solid var(--c-border)',
                fontSize: '12px',
                outline: 'none'
              }}
            />
            <button
              aria-label="Gửi câu hỏi"
              disabled={isTyping || !input.trim()}
              onClick={() => handleSend()}
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '50%',
                backgroundColor: 'var(--c-primary)',
                color: '#FFFFFF',
                border: 'none',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer'
              }}
            >
              <Send size={15} />
            </button>
          </div>
        </div>
      )}
    </>
  );
};
