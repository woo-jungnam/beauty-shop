export const ticketDestination = ticket => ticket.benefitType === 'TREATMENT_COURSE'
  ? `treatments?tab=courses${ticket.treatmentCourseId ? `&courseId=${ticket.treatmentCourseId}` : ''}`
  : `booking?ticketId=${ticket.id}${ticket.services?.find(s => s.available > 0)?.serviceId ? `&serviceId=${ticket.services.find(s => s.available > 0).serviceId}` : ''}`;
export const orderBenefitDestination = order => order.spaBenefitType === 'TREATMENT_COURSE' ? 'treatments?tab=courses' : 'profile?tab=tickets';
export const movementLabel = operation => ({ RESERVE: 'Giữ lượt đặt lịch', RELEASE: 'Giải phóng lượt giữ', REDEEM: 'Sử dụng dịch vụ', FORFEIT: 'Mất lượt theo chính sách', EXTEND: 'Gia hạn', COMPENSATE: 'Bù lượt', REFUND_CLOSE: 'Quyết toán và thu hồi vé' })[operation] || operation;
