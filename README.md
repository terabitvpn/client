<h1 align="center">Terabit VPN</h1>

<p align="center">
VPN-клиент на ядре Xray для Android, iOS, Windows, macOS и Linux.<br>
Подписки VLESS · VMess · Trojan · Shadowsocks · Hysteria2 · XHTTP · Reality.
</p>

## Ссылки

| | |
|---|---|
| Сайт | [terabitvpn.ru](https://terabitvpn.ru) |
| Telegram-бот | [@terabitvpnbot](https://t.me/terabitvpnbot) |
| Личный кабинет | [cabinet.terabitvpn.ru](https://cabinet.terabitvpn.ru) |

Приложение работает с любой подпиской VLESS / VMess / Trojan / Shadowsocks / Hysteria2.

---

## Возможности

- **Подключение в одно касание** — кнопка-«орб», таймер сессии, скорость загрузки и отдачи в реальном времени.
- **Любые подписки** — ссылка, base64, JSON-конфиги Xray (как в Happ), диплинки `terabit://`, `happ://add/`, QR-код, «Поделиться → Terabit».
- **Быстрый выбор сервера**
  - подписка скачивается наперегонки: напрямую, через зеркало и через уже поднятый туннель — побеждает первый ответ;
  - двухфазный пинг: мгновенный TCP-пинг всех серверов, затем реальная задержка через ядро;
  - на ПК подключение готово, как только локальный порт отвечает (~100 мс).
- **Умная маршрутизация** — российские сайты напрямую, остальное через VPN; свои правила «напрямую / через VPN / блок»; блокировка рекламы.
- **Сторож соединения** — каждые 20 секунд проверяет, что трафик реально идёт через туннель, и сам переходит на рабочий сервер.
- **Умные белые списки** — уходит на серверы белых списков при блокировках мобильного интернета и возвращается на обычные, когда ограничения снимут.
- **Автосмена сервера** при падении, автоподключение, запуск при включении устройства.
- **Раздельное туннелирование по приложениям** (Android), плитка в шторке, kill switch через системную «Постоянную VPN».
- **Локальный прокси** SOCKS5 + HTTP с логином и паролем (`terabit_…`), настраиваемые порты.
- **ПК:** режим TUN (весь трафик) или системный прокси, трей, тёмный заголовок окна.
- DNS через туннель (Cloudflare, Google, Quad9, AdGuard или свой), IPv6, MTU, мультиплекс, фрагментация TLS.

## Сборка

Нужны JDK 21 и Android SDK (для Android).

```bash
./gradlew :androidApp:assembleRelease       # APK по архитектурам + универсальный
./gradlew :desktopApp:run                   # запустить на ПК
./gradlew :desktopApp:packageDistributionForCurrentOS   # MSI/EXE, DMG или DEB/RPM
./gradlew :shared:jvmTest                   # тесты парсеров и сборщика конфигов
```

Ядро Xray скачивается при сборке и проверяется по sha256 (`gradle/libs.versions.toml`):
Android — [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite), ПК — [Xray-core](https://github.com/XTLS/Xray-core).
iOS-приложение собирается на macOS (см. `iosApp/`).

## Оформление

- Шрифт: Beeline Sans Black (`shared/src/commonMain/composeResources/font/`).
- Цвета: фирменный зелёный `#014320` и его светлый оттенок для текста и свечения на тёмном фоне — `ui/theme/Theme.kt`.
- Иконки и знак «T» собираются одной командой: `python3 tools/make_icons.py` (нужны fonttools и Pillow).

## Устройство

```
shared/        общий код (Kotlin Multiplatform + Compose Multiplatform)
  core/        модели, парсер ссылок и подписок, сборщик конфига Xray, контроллер
  ui/          весь интерфейс: тема, компоненты, экраны, десктопная оболочка
androidApp/    VpnService (TUN-дескриптор напрямую в ядро Xray), плитка, QR, автозапуск
desktopApp/    процесс Xray, системный прокси (Windows/macOS/Linux), трей, автозапуск
iosApp/        оболочка SwiftUI
```

## Лицензия и происхождение

Проект — форк открытого клиента [Ghostly VPN](https://github.com/Nelxi/ghostly-vpn) и распространяется под GPL-3.0 (`LICENSE`).
Исходный код любой сборки, которую вы отдаёте другим людям, нужно публиковать под той же лицензией.
Ядро Xray — MPL-2.0, AndroidLibXrayLite — LGPL-3.0. Подробности — в `licenses/NOTICE.md`.
Шрифт Beeline Sans не входит в GPL: нужна собственная лицензия на него.
