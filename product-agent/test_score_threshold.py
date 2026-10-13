import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

tests = [
    ('Có loại kem chống nắng nào bôi lên mát da thấm nhanh không?', 'Sufficient Detail - Product'),
    ('Tìm serum cấp ẩm phục hồi cho da dầu mụn', 'Sufficient Detail - Concern + Category'),
    ('Anessa có loại nào cho da dầu không?', 'Sufficient Detail - Brand'),
    ('Bảng giá triệt lông nách bên mình sao ạ?', 'Sufficient Detail - Service'),
    ('Bên mình có gói chăm sóc chi hông shop?', 'Vague Query - Should Clarify'),
    ('Ủa tiệm có mần đẹp hông dợ?', 'Vague Query - Should Clarify'),
]

for q, desc in tests:
    req = urllib.request.Request(
        'http://127.0.0.1:8000/chat',
        headers={'Content-Type': 'application/json'},
        data=json.dumps({'message': q, 'session_id': 'test_' + str(abs(hash(q)))}).encode('utf-8')
    )
    try:
        res = urllib.request.urlopen(req, timeout=30)
        data = json.loads(res.read().decode('utf-8'))
        products = data.get('products') or []
        und = data.get('understanding') or {}
        has_clarify = und.get('needs_clarification', False)
        score = und.get('information_score', 0.0)
        reply = data.get('reply', '')[:120].replace('\n', ' ')
        print(f'[{desc}]')
        print(f'  Query: {q}')
        print(f'  Score: {score:.1f} | Clarify: {has_clarify} | Products: {len(products)}')
        print(f'  Reply: {reply}...')
        print('-' * 60)
    except Exception as e:
        print(f'Error on {q}: {e}')
