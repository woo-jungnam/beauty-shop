import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

DateTime vietnamTime(DateTime date) =>
    date.toUtc().add(const Duration(hours: 7));

class DeliveryDates extends StatelessWidget {
  const DeliveryDates({super.key, required this.range, required this.onChange});

  final DateTimeRange range;
  final ValueChanged<DateTimeRange> onChange;

  static DateTime today() => DateUtils.dateOnly(vietnamTime(DateTime.now()));
  static DateTimeRange month() {
    final now = today();
    return DateTimeRange(start: DateTime(now.year, now.month), end: now);
  }

  static String apiDate(DateTime date) => DateFormat('yyyy-MM-dd').format(date);

  @override
  Widget build(BuildContext context) {
    final now = today();
    final format = DateFormat('dd/MM/yyyy');
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 8,
            children: [
              ActionChip(
                label: const Text('Hôm nay'),
                onPressed: () {
                  onChange(DateTimeRange(start: now, end: now));
                },
              ),
              ActionChip(
                label: const Text('Tuần này'),
                onPressed: () {
                  onChange(
                    DateTimeRange(
                      start: DateTime(
                        now.year,
                        now.month,
                        now.day - now.weekday + 1,
                      ),
                      end: now,
                    ),
                  );
                },
              ),
              ActionChip(
                label: const Text('Tháng này'),
                onPressed: () => onChange(month()),
              ),
            ],
          ),
          TextButton.icon(
            icon: const Icon(Icons.date_range),
            label: Text(
              '${format.format(range.start)} – ${format.format(range.end)}',
            ),
            onPressed: () async {
              final selected = await showDateRangePicker(
                context: context,
                firstDate: DateTime(2000),
                lastDate: now,
                currentDate: now,
                initialDateRange: range,
                saveText: 'Áp dụng',
              );
              if (selected != null && context.mounted) onChange(selected);
            },
          ),
        ],
      ),
    );
  }
}
