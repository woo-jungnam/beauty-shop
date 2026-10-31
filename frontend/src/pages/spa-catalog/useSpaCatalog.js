import { useCallback, useEffect, useRef, useState } from 'react';
import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { spaErrorMessage } from '../../shared/utils/spaMessages';
import { asList, unwrap } from './catalogUtils';

const allPages = async (endpoint, signal) => {
  const rows = [];
  for (let page = 0; ; page += 1) {
    const response = await apiClient.get(`${endpoint}?page=${page}&size=100&sort=id,asc`, { signal });
    const data = unwrap(response);
    if (!Array.isArray(data) && !Array.isArray(data?.content)) throw new Error('Dữ liệu danh mục chưa đúng định dạng. Vui lòng làm mới.');
    rows.push(...asList(response));
    if (Array.isArray(data) || data.last === true || page + 1 >= (data.totalPages || 1)) break;
  }
  return Array.from(new Map(rows.map(item => [item.id, item])).values());
};

export const useSpaCatalog = () => {
  const [catalog, setCatalog] = useState({ services: [], categories: [], packages: [], staff: [], facilities: [] });
  const [loading, setLoading] = useState(true);
  const [errors, setErrors] = useState({});
  const [ready, setReady] = useState({});
  const controller = useRef(null);
  const refresh = useCallback(async () => {
    controller.current?.abort();
    const request = new AbortController();
    controller.current = request;
    setLoading(true);
    const sources = [
      ['services', 'dịch vụ', () => allPages(ENDPOINTS.SPA.SERVICES, request.signal)],
      ['categories', 'danh mục liệu trình', () => allPages(ENDPOINTS.SPA.CATEGORIES, request.signal)],
      ['packages', 'gói liệu trình', async () => asList(await apiClient.get(ENDPOINTS.SPA.PACKAGES, { signal: request.signal }))],
      ['staff', 'kỹ năng KTV', async () => asList(await apiClient.get(ENDPOINTS.SPA.STAFF, { signal: request.signal }))],
      ['facilities', 'phòng và thiết bị', async () => asList(await apiClient.get(ENDPOINTS.SPA.FACILITIES, { signal: request.signal }))],
    ];
    const results = await Promise.allSettled(sources.map(([, , load]) => load()));
    if (request.signal.aborted) return;
    const next = {}; const failures = {}; const available = {};
    results.forEach((result, index) => {
      const [key, name] = sources[index];
      available[key] = result.status === 'fulfilled';
      if (result.status === 'fulfilled') next[key] = result.value;
      else failures[key] = spaErrorMessage(result.reason, `Chưa tải được ${name}. Vui lòng làm mới để thử lại.`);
    });
    setCatalog(previous => ({ ...previous, ...next }));
    setErrors(failures); setReady(available); setLoading(false);
  }, []);
  useEffect(() => { refresh(); return () => controller.current?.abort(); }, [refresh]);
  return { ...catalog, loading, errors, ready, refresh };
};
