# Декларации Google Play и App Store: что заполнять

Черновик для заполнения консолей. Сверять с фактическим кодом и политикой.
После изменений в потоках данных обновлять.

## 1. Google Play → Data safety

### 1.1. Какие данные собираются

| Категория Google | Что у нас | Передаётся с сервера | Можно ли удалить |
|---|---|---|---|
| Personal info → Name | имя, фамилия, display name | да | да |
| Personal info → Email address | email для входа | да | да |
| Personal info → Phone number | телефон в профиле, если указан | да | да |
| Personal info → User IDs | user_id, username, auth subject | да | да |
| Messages → Emails or text messages | сообщения чата | да | да |
| Messages → Other in-app messages | служебные/системные сообщения | да | да |
| Photos and videos → Photos | аватары, изображения в чатах/новостях | да | да |
| Photos and videos → Videos | видео, видео-кружки | да | да |
| Files and docs → Files and docs | документы и вложения | да | да |
| Audio → Voice or sound recordings | голосовые сообщения | да | да |
| Contacts → Contacts | контакты внутри muzea, не телефонная книга | да | да |
| App activity → In-app search history | поиск GIF | да | да |
| App activity → Other user-generated content | публикации, комментарии, реакции | да | да |
| App activity → Other actions | онлайн-статус, последний визит | да | да |
| Device or other IDs → Device or other IDs | device_id (UUID), push-токен | да | да |
| Diagnostics → Crash logs | ошибки/краши | да | да |
| Diagnostics → Other diagnostics logs | технические логи | да | да |

Если контакты внутри приложения не считаются «Contacts» в терминах Google,
выбрать «Other user-generated content» и пояснить. Телефонная книга не читается.

### 1.2. Передача третьим лицам

| Получатель | Данные | Зачем |
|---|---|---|
| Google (FCM) | push-токен, тип/ID уведомления, текст, имя, чат | push |
| Apple (APNs) | push-токен, payload | push iOS |
| KLIPY | поисковый запрос, IP | поиск GIF |
| Mail.ru | email, код входа | доставка писем |
| Сторонние сайты | URL, IP | превью ссылок |

В Data safety указать передачу, если она подпадает под определение Google.
Для процессоров (Mail.ru) проверить, нужно ли указывать как «shared».

### 1.3. Безопасность

- Данные шифруются при передаче: **да** (HTTPS/TLS).
- Пользователь может запросить удаление: **да**.
- Данные не продаются: **да**.
- Удаление аккаунта: в приложении + `https://muzea.su/delete-account`.
- Период восстановления: 30 дней. Аккаунт сразу скрывается, вход закрывается,
  push и сессии отзываются. В течение 30 дней можно восстановить через
  поддержку; после — данные удаляются.
- Что удаляется: профиль, сообщения, вложения, контент, контакты, токены.
- Что остаётся: tombstone без текста, бэкапы 7 дней, обезличенные логи.

## 2. Apple → App Privacy

### 2.1. Data Linked to You

| Категория Apple | Что у нас | Цели |
|---|---|---|
| Contact Info → Name | имя, фамилия, display name | App Functionality |
| Contact Info → Email Address | email | App Functionality |
| Contact Info → Phone Number | телефон | App Functionality |
| User Content → Emails or Text Messages | сообщения | App Functionality |
| User Content → Photos or Videos | фото/видео | App Functionality |
| User Content → Audio Data | голосовые | App Functionality |
| User Content → Other User Content | публикации, комментарии, реакции | App Functionality |
| Identifiers → User ID | user_id, username | App Functionality |
| Identifiers → Device ID | device_id, push-токен | App Functionality |
| Usage Data → Product Interaction | онлайн-статус, активность | App Functionality |
| Usage Data → Other Usage Data | последний визит, служебные события | App Functionality |
| Search History → Search History | поиск GIF | App Functionality |
| Diagnostics → Crash Data | краши | App Functionality |
| Diagnostics → Other Diagnostic Data | ошибки, логи | App Functionality |

`Contacts` указывать только если читается телефонная книга. Сейчас — нет.

### 2.2. Tracking

- Отслеживание: **нет**.
- `NSPrivacyTracking=false`.
- ATT не нужен.
- IDFA не используется.

### 2.3. Privacy manifest

`Muzea/Resources/PrivacyInfo.xcprivacy` должен содержать:

- EmailAddress, Name, UserID, DeviceID;
- PhotosorVideos, OtherUserContent;
- EmailsOrTextMessages, AudioData;
- SearchHistory, OtherUsageData;
- CrashData, OtherDiagnosticData;
- UserDefaults (CA92.1), FileTimestamp (C617.1);
- без Contacts, если телефонная книга не читается.

### 2.4. Удаление аккаунта

- Кнопка в приложении: «Профиль» → «Удалить аккаунт».
- Публичная страница: `https://muzea.su/delete-account`.
- После запроса: аккаунт скрывается, вход и push отключаются.
- В течение 30 дней: восстановление через поддержку с новым согласием.
- После 30 дней: профиль, сообщения, вложения, контент, контакты и токены
  удаляются; в чатах остаются tombstone без текста; бэкапы 7 дней.

## 3. UGC

Google Play и App Store требуют:

- правила пользовательского контента;
- жалобу на контент;
- блокировку пользователя;
- модерацию и сроки;
- контакт поддержки.

Сейчас есть жалобы и блокировки в чате, новостях и видео. Нужно проверить:

- срок реакции на жалобу (лучше 24 часа на подтверждение, 3–10 дней на решение);
- контакт в приложении и политике;
- фильтрация запрещённого контента;
- возрастной рейтинг.

## 4. Возраст

- В терминах: 14+.
- Добавить подтверждение 14+ в приложении.
- В Google Play: target audience 14+, content rating.
- В App Store: age rating 14+/17+ по анкете; UGC может требовать 17+.

## 5. Демо-доступ для ревью

- Google Play: демо-аккаунт или фиксированный OTP.
- App Store: демо-аккаунт с фиксированным кодом.
- Не хранить демо-код в публичном репозитории; задавать через секреты.

## 6. Чек-лист

- [ ] Data safety заполнена по таблице 1.1.
- [ ] Передача Google/Apple/KLIPY указана.
- [ ] Удаление аккаунта и URL указаны.
- [ ] App Privacy заполнена по таблице 2.1.
- [ ] Tracking = нет.
- [ ] PrivacyInfo исправлен.
- [ ] UGC: жалобы, блокировки, контакт, сроки.
- [ ] Возраст 14+.
- [ ] Демо-доступ.
- [ ] Политика и удаление совпадают с кодом.
