import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../providers/shipper_provider.dart';
import '../delivery/delivery_dates.dart';

class NotificationScreen extends StatefulWidget {
  const NotificationScreen({super.key});

  @override
  State<NotificationScreen> createState() => _NotificationScreenState();
}

class _NotificationScreenState extends State<NotificationScreen> {
  int _page = 0;
  int? _marking;
  late Future<Map<String, dynamic>> _notifications;

  @override
  void initState() {
    super.initState();
    _notifications = context.read<ShipperProvider>().fetchNotifications(_page);
  }

  Future<void> _load(int page) async {
    final provider = context.read<ShipperProvider>();
    setState(() {
      _page = page;
      _notifications = provider.fetchNotifications(page);
    });
    try {
      await _notifications;
    } catch (_) {
      // The FutureBuilder displays the request error.
    }
    if (mounted) await provider.refreshUnreadCount();
  }

  Future<void> _markRead(Map<String, dynamic> notification) async {
    if (_marking != null || notification['readAt'] != null) return;
    final id = notification['id'] as int;
    setState(() => _marking = id);
    try {
      await context.read<ShipperProvider>().markRead(id);
      if (mounted) await _load(_page);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) setState(() => _marking = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final dateFormat = DateFormat('dd/MM/yyyy HH:mm');
    return Scaffold(
      appBar: AppBar(title: const Text('Thông báo')),
      body: FutureBuilder(
        future: _notifications,
        builder: (context, snapshot) {
          final data = snapshot.data;
          final rows =
              (data?['content'] as List?)?.cast<Map<String, dynamic>>() ?? [];
          return RefreshIndicator(
            onRefresh: () => _load(0),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(12),
              children: [
                if (snapshot.connectionState != ConnectionState.done)
                  const Center(child: CircularProgressIndicator())
                else if (snapshot.hasError) ...[
                  Text(snapshot.error.toString()),
                  TextButton(
                    onPressed: () => _load(_page),
                    child: const Text('Thử lại'),
                  ),
                ] else ...[
                  if (rows.isEmpty)
                    const Center(child: Text('Chưa có thông báo')),
                  for (final row in rows)
                    Card(
                      child: ListTile(
                        leading: Icon(
                          row['readAt'] == null
                              ? Icons.notifications_active
                              : Icons.notifications_none,
                        ),
                        title: Text(row['title'] as String),
                        subtitle: Text(
                          [
                            row['body'] as String,
                            if (DateTime.tryParse('${row['createdAt']}')
                                case final date?)
                              dateFormat.format(vietnamTime(date)),
                          ].join('\n'),
                        ),
                        trailing: _marking == row['id']
                            ? const SizedBox.square(
                                dimension: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : null,
                        onTap: row['readAt'] == null
                            ? () => _markRead(row)
                            : null,
                      ),
                    ),
                  if ((_page > 0 || data?['last'] == false) && rows.isNotEmpty)
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        TextButton(
                          onPressed: _page > 0 ? () => _load(_page - 1) : null,
                          child: const Text('Trang trước'),
                        ),
                        TextButton(
                          onPressed: data?['last'] == false
                              ? () => _load(_page + 1)
                              : null,
                          child: const Text('Trang sau'),
                        ),
                      ],
                    ),
                ],
              ],
            ),
          );
        },
      ),
    );
  }
}
