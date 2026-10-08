export const getGuestSessionId = () => {
  try {
    let sessionId = localStorage.getItem('beautyshop_guest_session_id');
    if (!sessionId) {
      sessionId = 'guest_' + Math.random().toString(36).substring(2, 12) + '_' + Date.now();
      localStorage.setItem('beautyshop_guest_session_id', sessionId);
    }
    return sessionId;
  } catch {
    return 'guest_' + Date.now();
  }
};
