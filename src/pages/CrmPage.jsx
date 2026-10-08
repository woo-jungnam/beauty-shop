import React, { useEffect, useState } from 'react';
import { MessageSquarePlus, Search, Trash2 } from 'lucide-react';
import { apiClient } from '../shared/api/client';
import { ENDPOINTS } from '../shared/api/endpoints';
import { Button } from '../shared/ui/Button';
import { Input } from '../shared/ui/Input';
import { Select } from '../shared/ui/Select';
import { Modal } from '../shared/ui/Modal';
import { DataTable } from '../shared/ui/DataTable';
import { Badge } from '../shared/ui/Badge';
import { formatCurrency, formatDateTime } from '../shared/utils/formatters';

export const CrmPage = () => {
  const [customers, setCustomers] = useState([]);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState(null);
  const [notes, setNotes] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({ noteType: 'GENERAL', content: '', skinProfile: '', allergies: '', contraindications: '', followUpAt: '' });

  const search = async () => { setLoading(true); try { const res = await apiClient.get(`${ENDPOINTS.CRM.CUSTOMERS}?keyword=${encodeURIComponent(keyword)}`); setCustomers(res.data || res || []); } catch (err) { alert(err.message); } finally { setLoading(false); } };
  useEffect(() => { search(); }, []);
  const openCustomer = async (customer) => { setSelected(customer); const res = await apiClient.get(ENDPOINTS.CRM.NOTES(customer.id)); setNotes(res.data || res || []); setModalOpen(true); };
  const addNote = async (e) => { e.preventDefault(); await apiClient.post(ENDPOINTS.CRM.NOTES(selected.id), { ...form, followUpAt: form.followUpAt ? new Date(form.followUpAt).toISOString() : null }); const res = await apiClient.get(ENDPOINTS.CRM.NOTES(selected.id)); setNotes(res.data || res || []); setForm({ noteType: 'GENERAL', content: '', skinProfile: '', allergies: '', contraindications: '', followUpAt: '' }); };
  const deleteNote = async (noteId) => {
    if (!window.confirm('Xóa ghi chú chăm sóc này?')) return;
    try {
      await apiClient.delete(ENDPOINTS.CRM.DELETE_NOTE(noteId));
      const res = await apiClient.get(ENDPOINTS.CRM.NOTES(selected.id));
      setNotes(res.data || res || []);
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa ghi chú');
    }
  };
  const columns = [
    { header: 'Khách hàng', accessor: (row) => <div><strong>{row.fullName || row.username}</strong><br/><small>{row.username} · {row.phone || row.email || 'Chưa cập nhật'}</small></div> },
    { header: 'Hạng', accessor: (row) => <Badge variant="info">{row.membershipTier || 'MEMBER'}</Badge> },
    { header: 'Điểm', accessor: (row) => row.loyaltyPoints || 0, align: 'right' },
    { header: 'Đơn hàng', accessor: (row) => row.orderCount || 0, align: 'right' },
    { header: 'Lịch spa', accessor: (row) => row.appointmentCount || 0, align: 'right' },
    { header: 'Giá trị vòng đời', accessor: (row) => <strong>{formatCurrency(row.lifetimeValue || 0)}</strong>, align: 'right' },
    { header: 'Chăm sóc', align: 'right', accessor: (row) => <Button size="sm" icon={MessageSquarePlus} onClick={() => openCustomer(row)}>Hồ sơ 360°</Button> },
  ];
  return <div className="content-container">
    <div className="page-header"><div><h1 className="page-title">CRM & Hồ sơ chăm sóc khách hàng</h1><p className="page-subtitle">Lịch sử giá trị khách hàng, tình trạng da, dị ứng và kế hoạch theo dõi</p></div></div>
    <div className="card"><form onSubmit={(e)=>{e.preventDefault();search();}} style={{display:'flex',gap:8,marginBottom:14}}><Input icon={Search} value={keyword} onChange={(e)=>setKeyword(e.target.value)} placeholder="Tên, tài khoản, email hoặc số điện thoại"/><Button type="submit">Tìm kiếm</Button></form><DataTable columns={columns} data={customers} loading={loading}/></div>
    <Modal isOpen={modalOpen} onClose={()=>setModalOpen(false)} title={`Hồ sơ chăm sóc: ${selected?.fullName || ''}`} maxWidth="820px">
      <div className="grid-3" style={{marginBottom:16}}><div><small>Giá trị vòng đời</small><strong style={{display:'block'}}>{formatCurrency(selected?.lifetimeValue)}</strong></div><div><small>Đơn hàng</small><strong style={{display:'block'}}>{selected?.orderCount}</strong></div><div><small>Lịch spa</small><strong style={{display:'block'}}>{selected?.appointmentCount}</strong></div></div>
      <form onSubmit={addNote} className="card" style={{marginBottom:16}}><Select label="Loại ghi chú" value={form.noteType} onChange={(e)=>setForm({...form,noteType:e.target.value})} options={[{label:'Chăm sóc chung',value:'GENERAL'},{label:'Đánh giá da',value:'SKIN_ASSESSMENT'},{label:'Theo dõi liệu trình',value:'TREATMENT'},{label:'Khiếu nại',value:'COMPLAINT'}]}/><label className="form-label">Nội dung *</label><textarea required className="form-textarea" rows="3" value={form.content} onChange={(e)=>setForm({...form,content:e.target.value})}/><div className="grid-2" style={{marginTop:10}}><Input label="Tình trạng da" value={form.skinProfile} onChange={(e)=>setForm({...form,skinProfile:e.target.value})}/><Input label="Dị ứng" value={form.allergies} onChange={(e)=>setForm({...form,allergies:e.target.value})}/><Input label="Chống chỉ định" value={form.contraindications} onChange={(e)=>setForm({...form,contraindications:e.target.value})}/><Input label="Hẹn chăm sóc lại" type="datetime-local" value={form.followUpAt} onChange={(e)=>setForm({...form,followUpAt:e.target.value})}/></div><Button type="submit" style={{marginTop:12}}>Lưu ghi chú</Button></form>
      <div style={{display:'grid',gap:8}}>{notes.map((note)=><div key={note.id} style={{border:'1px solid var(--border-subtle)',padding:12}}><div style={{display:'flex',justifyContent:'space-between',alignItems:'center'}}><div style={{display:'flex',gap:8,alignItems:'center'}}><Badge variant="info">{note.noteType}</Badge><small>{formatDateTime(note.createdAt)}</small></div><Button size="sm" variant="danger" icon={Trash2} onClick={()=>deleteNote(note.id)} title="Xóa ghi chú" /></div><p style={{marginTop:8}}>{note.content}</p>{note.skinProfile&&<small>Da: {note.skinProfile}</small>}{note.allergies&&<div><small>Dị ứng: {note.allergies}</small></div>}{note.contraindications&&<div><small>Chống chỉ định: {note.contraindications}</small></div>}</div>)}</div>
    </Modal>
  </div>;
};
