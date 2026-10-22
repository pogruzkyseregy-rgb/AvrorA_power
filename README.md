# AvrorA_power

Android-приложение для мониторинга батареи в стиле HUD (тема Авроры).

## Что показывает
- Заряд кольцом с делениями, статус зарядки
- Анимацию при подключении зарядки
- Напряжение, ток, температуру, источник (сеть / USB / беспроводная)
- Время до полной зарядки (Android 9+, при зарядке)
- Состояние батареи, технологию
- ОЗУ и занятую память (используется / всего)

## Сборка
APK собирается автоматически через GitHub Actions при пуше в `main`
(вкладка Actions → Build APK → артефакт `AvrorA_power-debug`).

Локально: `gradle :app:assembleDebug` (нужны JDK 17 и Android SDK).

## Структура
- `app/src/main/java/com/avrora/power/BatteryMonitor.kt` — чтение данных батареи, RAM и памяти
- `app/src/main/java/com/avrora/power/MainActivity.kt` — экран и анимации
- `app/src/main/java/com/avrora/power/ui/Hud.kt` — HUD-компоненты (фон, панели, шкалы, кольцо)
