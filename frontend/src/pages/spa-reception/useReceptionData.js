import { useCallback, useEffect, useRef, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { spaErrorMessage } from '../../shared/utils/spaMessages';
import { asList, unwrap } from './receptionUtils';

const loadAppointments = async signal => {
  const appointments = [];
  for (let page = 0; ; page += 1) {
    const response = await apiClient.get(`${ENDPOINTS.SPA.APPOINTMENTS}?size=100&page=${page}`, { signal });
    const data = unwrap(response);
    const rows = asList(response);
    appointments.push(...rows);
    if (Array.isArray(data) || data.last === true || page + 1 >= (data.totalPages || 1)) break;
  }
  return Array.from(new Map(appointments.map(item => [item.id, item])).values());
};

export const useReceptionData = () => {
  const [data, setData] = useState({ appointments: [], facilities: [], blocks: {}, updatedAt: 0, appointmentsReady: false, appointmentsError: '', facilitiesReady: false });
  const [services, setServices] = useState([]);
  const [staff, setStaff] = useState([]);
  const [refreshing, setRefreshing] = useState(true);
  const [error, setError] = useState('');
  const controller = useRef(null);
  const inFlight = useRef(false);
  const refresh = useCallback(async () => {
    controller.current?.abort();
    const request = new AbortController();
    controller.current = request;
    inFlight.current = true;
    setRefreshing(true);
    try {
      const [appointmentResult, facilityResult] = await Promise.allSettled([
        loadAppointments(request.signal), apiClient.get(ENDPOINTS.SPA.FACILITIES, { signal: request.signal }),
      ]);
      if (request.signal.aborted) return;
      const appointmentsLoaded = appointmentResult.status === 'fulfilled';
      const facilitiesLoaded = facilityResult.status === 'fulfilled';
      const facilities = facilitiesLoaded ? asList(facilityResult.value) : [];
      setData(previous => ({
        ...previous,
        appointments: appointmentsLoaded ? appointmentResult.value : previous.appointments,
        appointmentsReady: appointmentsLoaded || previous.appointmentsReady,
        appointmentsError: appointmentsLoaded ? '' : spaErrorMessage(appointmentResult.reason, 'Chưa tải được lịch hẹn. Vui lòng thử lại.'),
        facilities: facilitiesLoaded ? facilities : previous.facilities,
        facilitiesReady: facilitiesLoaded,
        blocks: {}, updatedAt: 0,
      }));
      setError(facilitiesLoaded ? '' : spaErrorMessage(facilityResult.reason, 'Chưa tải được phòng / giường. Vui lòng làm mới trước khi gán phòng.'));
      if (!appointmentsLoaded || !facilitiesLoaded) return;
      const blockResults = await Promise.allSettled(facilities.map(facility =>
        apiClient.get(ENDPOINTS.SPA.FACILITY_BLOCKS(facility.id), { signal: request.signal })));
      if (request.signal.aborted) return;
      const blocks = {};
      blockResults.forEach((result, index) => {
        if (result.status === 'fulfilled') blocks[facilities[index].id] = asList(result.value);
      });
      setData(previous => ({ ...previous, blocks, updatedAt: Date.now() }));
      setError(blockResults.some(result => result.status === 'rejected')
        ? 'Chưa tải được lịch tạm dừng của một số phòng. Vui lòng làm mới trước khi gán phòng.' : '');
    } catch (err) {
      if (!request.signal.aborted) {
        setData(previous => ({ ...previous, blocks: {}, updatedAt: 0, facilitiesReady: false }));
        setError(spaErrorMessage(err, 'Chưa tải được dữ liệu quầy. Vui lòng làm mới để kiểm tra phòng và lịch hẹn.'));
      }
    } finally {
      if (!request.signal.aborted) { inFlight.current = false; setRefreshing(false); }
    }
  }, []);
  useEffect(() => {
    refresh();
    const timer = setInterval(() => { if (!document.hidden && !inFlight.current) refresh(); }, 30000);
    const onVisible = () => { if (!document.hidden && !inFlight.current) refresh(); };
    document.addEventListener('visibilitychange', onVisible);
    return () => { clearInterval(timer); document.removeEventListener('visibilitychange', onVisible); controller.current?.abort(); };
  }, [refresh]);
  useEffect(() => {
    const request = new AbortController();
    const load = async () => {
      const results = await Promise.allSettled([
        apiClient.get(ENDPOINTS.SPA.PUBLIC_SERVICES, { signal: request.signal }),
        apiClient.get(ENDPOINTS.SPA.STAFF, { signal: request.signal }),
      ]);
      if (request.signal.aborted) return;
      if (results[0].status === 'fulfilled') setServices(asList(results[0].value));
      else setError(spaErrorMessage(results[0].reason, 'Chưa tải được danh mục dịch vụ. Vui lòng tải lại trang.'));
      if (results[1].status === 'fulfilled' && asList(results[1].value).length) setStaff(asList(results[1].value));
      else {
        try { const response = await apiClient.get(ENDPOINTS.SPA.PUBLIC_STAFF, { signal: request.signal }); if (!request.signal.aborted) setStaff(asList(response)); }
        catch (err) { if (!request.signal.aborted) setError(spaErrorMessage(err, 'Chưa tải được danh sách KTV. Vui lòng tải lại trang.')); }
      }
    };
    load();
    return () => request.abort();
  }, []);
  return { ...data, services, staff, refreshing, error, refresh };
};
