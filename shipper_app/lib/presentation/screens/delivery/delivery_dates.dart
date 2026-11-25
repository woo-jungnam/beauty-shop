import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/shipper_formatters.dart';

DateTime vietnamTime(DateTime date) => toVietnamTime(date);

class DeliveryDates extends StatelessWidget {
  const DeliveryDates({super.key, required this.range, required this.onChange});

  final DateTimeRange range;
  final ValueChanged<DateTimeRange> onChange;

  static DateTime today() => DateUtils.dateOnly(toVietnamTime(DateTime.now()));

  static DateTimeRange week() {
    final now = today();
    return DateTimeRange(
      start: DateUtils.dateOnly(now.subtract(Duration(days: now.weekday - 1))),
      end: now,
    );
  }

  static DateTimeRange month() {
    final now = today();
    return DateTimeRange(start: DateTime(now.year, now.month), end: now);
  }

  static String apiDate(DateTime date) =>
      '${date.year.toString().padLeft(4, '0')}-'
      '${date.month.toString().padLeft(2, '0')}-'
      '${date.day.toString().padLeft(2, '0')}';

  bool _matches(DateTimeRange target) =>
      DateUtils.isSameDay(range.start, target.start) &&
      DateUtils.isSameDay(range.end, target.end);

  @override
  Widget build(BuildContext context) {
    final now = today();
    final todayRange = DateTimeRange(start: now, end: now);
    final weekRange = week();
    final monthRange = month();

    final presets = <(String, DateTimeRange, bool)>[
      ('Hôm nay', todayRange, _matches(todayRange)),
      ('Tuần này', weekRange, _matches(weekRange)),
      ('Tháng này', monthRange, _matches(monthRange)),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: 8,
          runSpacing: 6,
          children: [
            for (final (label, targetRange, selected) in presets)
              ChoiceChip(
                label: Text(label),
                selected: selected,
                showCheckmark: false,
                selectedColor: AppColors.primarySurface,
                labelStyle: TextStyle(
                  fontSize: 13,
                  fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                  color: selected ? AppColors.primaryDark : AppColors.textMain,
                ),
                side: BorderSide(
                  color: selected ? AppColors.primary : AppColors.border,
                ),
                onSelected: (_) => onChange(targetRange),
              ),
          ],
        ),
        const SizedBox(height: 8),
        Material(
          color: AppColors.card,
          borderRadius: BorderRadius.circular(ShipperLayout.innerRadius),
          child: InkWell(
            borderRadius: BorderRadius.circular(ShipperLayout.innerRadius),
            onTap: () async {
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
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(ShipperLayout.innerRadius),
                border: Border.all(color: AppColors.border),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.date_range_outlined,
                    size: 18,
                    color: AppColors.primary,
                  ),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Text(
                      '${formatVnDate(range.start)} – ${formatVnDate(range.end)}',
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textMain,
                      ),
                    ),
                  ),
                  const SizedBox(width: 4),
                  const Icon(
                    Icons.keyboard_arrow_down_rounded,
                    size: 18,
                    color: AppColors.textMuted,
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}
