import { useEffect, useRef } from 'react';

// Share keyboard and focus behavior between customer drawers without coupling their UI.
export const useCustomerDialog = (open, dialogRef, onClose) => {
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const dialog = dialogRef.current;
    const getFocusable = () => [...(dialog?.querySelectorAll(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]',
    ) || [])].filter((element) => element.getClientRects().length > 0);
    (getFocusable()[0] || dialog)?.focus({ preventScroll: true });

    const handleKey = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeRef.current();
      }
      if (event.key !== 'Tab') return;
      const elements = getFocusable();
      const first = elements[0];
      const last = elements.at(-1);
      if (!first) {
        event.preventDefault();
        dialog?.focus();
      } else if (event.shiftKey && (document.activeElement === first || !dialog?.contains(document.activeElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !dialog?.contains(document.activeElement))) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('keydown', handleKey);
      document.body.style.overflow = previousOverflow;
      if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true });
    };
  }, [open, dialogRef]);
};
