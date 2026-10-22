import { apiClient } from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/endpoints';
import { getGuestSessionId } from '../../shared/utils/session';

const record = (productId, actionType) => {
  try {
    apiClient.post(ENDPOINTS.CATALOG.TRACKING_INTERACTION, {
      productId: Number(productId), actionType, sessionId: getGuestSessionId(),
    }).catch(() => {});
  } catch {}
};

export const recordSearchClick = productId => record(productId, 'SEARCH_CLICK');
export const recordProductView = productId => record(productId, 'VIEW');
