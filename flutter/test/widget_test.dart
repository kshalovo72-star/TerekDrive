import 'package:flutter_test/flutter_test.dart';
import 'package:terek_drive/main.dart';

void main() {
  test('season model smoke test', () {
    expect(Season.values.length, 4);
    expect(Season.winter.title, 'Зима');
    expect(Season.summer.title, 'Лето');
  });
}
