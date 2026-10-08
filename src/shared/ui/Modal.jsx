import React, { useEffect, useId, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { Button } from './Button';

// A shared stack keeps keyboard focus and body scrolling correct for nested dialogs.
const openDialogs = [];
let originalBodyOverflow = '';

export const Modal = ({
  isOpen,
  onClose,
  title,
  children,
  footer,
  maxWidth = '560px',
}) => {
  const dialogRef = useRef(null);
  const backdropRef = useRef(null);
  const closeRef = useRef(onClose);
  const tokenRef = useRef(Symbol('dialog'));
  const [present, setPresent] = useState(isOpen);
  const titleId = useId();

  useEffect(() => { closeRef.current = onClose; }, [onClose]);

  useEffect(() => {
    if (isOpen) {
      const frame = window.requestAnimationFrame(() => setPresent(true));
      return () => window.cancelAnimationFrame(frame);
    }
    const timer = window.setTimeout(() => setPresent(false), 170);
    return () => window.clearTimeout(timer);
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen || !present) return undefined;

    const token = tokenRef.current;
    const previousFocus = document.activeElement;
    const entry = { token, dialog: dialogRef.current };
    if (!openDialogs.length) {
      originalBodyOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
    }
    openDialogs.push(entry);
    if (backdropRef.current) backdropRef.current.style.zIndex = String(9999 + openDialogs.length);
    const isTopDialog = () => openDialogs.at(-1)?.token === token;
    const focusDialog = () => dialogRef.current?.focus({ preventScroll: true });

    const handleKeyDown = (event) => {
      if (!isTopDialog()) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopPropagation();
        closeRef.current?.();
        return;
      }
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const focusable = [...dialogRef.current.querySelectorAll(
        'button, input, select, textarea, [href], [contenteditable="true"], [tabindex]'
      )].filter((node) => !node.disabled && node.tabIndex >= 0 && node.getClientRects().length > 0 && !node.closest('[inert]'));
      const first = focusable[0];
      const last = focusable.at(-1);
      if (!first) {
        event.preventDefault();
        focusDialog();
      } else if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialogRef.current)) {
        event.preventDefault();
        first.focus();
      }
    };
    const handleFocusIn = (event) => {
      if (isTopDialog() && dialogRef.current && !dialogRef.current.contains(event.target)) focusDialog();
    };

    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('focusin', handleFocusIn);
    const frame = window.requestAnimationFrame(focusDialog);

    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('focusin', handleFocusIn);
      const wasTop = isTopDialog();
      const index = openDialogs.findIndex((item) => item.token === token);
      if (index >= 0) openDialogs.splice(index, 1);
      if (!openDialogs.length) document.body.style.overflow = originalBodyOverflow;
      if (wasTop) {
        const remainingDialog = openDialogs.at(-1)?.dialog;
        if (previousFocus?.isConnected && (!remainingDialog || remainingDialog.contains(previousFocus))) {
          previousFocus.focus?.({ preventScroll: true });
        } else {
          remainingDialog?.focus({ preventScroll: true });
        }
      }
    };
  }, [isOpen, present]);

  if (!present) return null;

  return (
    <div
      ref={backdropRef}
      className={`modal-backdrop ${isOpen ? 'is-open' : 'is-closing'}`}
      inert={!isOpen ? true : undefined}
      aria-hidden={!isOpen ? true : undefined}
      onClick={(event) => {
        if (isOpen && event.target === event.currentTarget && openDialogs.at(-1)?.token === tokenRef.current) closeRef.current?.();
      }}
    >
      <div ref={dialogRef} className="modal-window" style={{ maxWidth }} role="dialog" aria-modal="true" aria-labelledby={title ? titleId : undefined} aria-label={title ? undefined : 'Hộp thoại'} tabIndex={-1}>
        <div className="modal-header">
          <h3 id={titleId} className="modal-title">{title}</h3>
          <Button variant="ghost" size="sm" className="modal-close-btn" onClick={() => closeRef.current?.()} aria-label="Đóng cửa sổ">
            <X size={19} aria-hidden="true" />
          </Button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>
  );
};
