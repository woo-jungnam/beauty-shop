import React, { useState, useEffect } from 'react';
import { useAuth } from '../app/providers/AuthProvider';
import { TreatmentWorkspace } from '../features/treatments/TreatmentWorkspace';
import { SpaReceptionPage } from '../pages/SpaReceptionPage';
import './styles/staff-portal.css';

// ponytail: unified staff portal routing directly to single source of truth components
export const SpaStaffApp = ({ onSwitchToAdmin }) => {
  const { isAdmin } = useAuth();
  const [activeTab, setActiveTab] = useState(() =>
    window.location.hash.includes('staff/treatments') || window.location.hash.includes('treatments')
      ? 'treatments'
      : 'queue'
  );

  useEffect(() => {
    const syncRoute = () => {
      if (window.location.hash.includes('staff/treatments') || window.location.hash.includes('treatments')) {
        setActiveTab('treatments');
      } else {
        setActiveTab('queue');
      }
    };
    window.addEventListener('hashchange', syncRoute);
    return () => window.removeEventListener('hashchange', syncRoute);
  }, []);

  if (activeTab === 'treatments') {
    return <TreatmentWorkspace role={isAdmin ? 'admin' : 'staff'} />;
  }

  return <SpaReceptionPage onSwitchToAdmin={onSwitchToAdmin} />;
};
