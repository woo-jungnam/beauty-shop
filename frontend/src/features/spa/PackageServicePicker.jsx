import React from 'react';

export function PackageServicePicker({ ticket, serviceId, selected, onChange }) {
  const choices = ticket?.services?.filter(item => item.available > 0 && Number(item.serviceId) !== Number(serviceId)) || [];
  if (!choices.length) return null;
  return <fieldset>
    <legend>Thêm dịch vụ trong cùng lịch hẹn</legend>
    {choices.map(item => <label key={item.serviceId}>
      <input type="checkbox" checked={selected.includes(item.serviceId)}
        onChange={event => onChange(event.target.checked ? [...selected, item.serviceId] : selected.filter(id => id !== item.serviceId))} />
      {item.serviceName} · Còn {item.available} lượt
    </label>)}
  </fieldset>;
}
