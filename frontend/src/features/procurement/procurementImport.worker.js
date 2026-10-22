import { parseExcel } from './procurementImport';

self.onmessage = async ({ data }) => {
  try {
    self.postMessage({ rows: await parseExcel(data) });
  } catch (error) {
    self.postMessage({ error: error.message || 'Không đọc được file Excel.' });
  }
};
