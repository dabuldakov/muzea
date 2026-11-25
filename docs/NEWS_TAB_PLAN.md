# Вкладка «Новости»: фото, лайки, просмотры, редактирование и комментарии

> **Статус:** реализовано в Android и `makeup` (без push-уведомлений);
> iOS обновлён следом, сборка проверяется на macOS.
>
> Затрагивает три репозитория: Android (`muzea`), бэкенд `makeup` и
> iOS (`muzea-ios`). Контракт API сначала фиксируем здесь, затем реализуем
> на бэкенде и клиентах.

## 1. Что уже есть

### Android

- Лента `NewsListFragment` + `NewsAdapter`, деталь `NewsDetailFragment` в
  `ScrollView`, создание `CreateNewsActivity`.
- Домен `News(id, title, content, imageUrl, relatedVideo, author, publishedAt)`.
- Есть удаление новости автором (кнопка внизу детали), жалоба, связанное видео.
- Пагинация ленты по 20, фильтр «контакты + свои публикации» на клиенте.
- Обновление ленты при возврате на вкладку (`onResume`/`onHiddenChanged`).

### Бэкенд `makeup`

- `GET /api/news` — страница опубликованных новостей (проекция
  `NewsListItem`, nginx microcache 3 с).
- `GET /api/news/{id}` — деталь; `POST /api/news` — создание (multipart);
  `DELETE /api/news/{id}` — мягкое удаление автором.
- `POST /api/reports` — жалобы на `NEWS`/`VIDEO`.
- `news.image_key` → MinIO, раздача `GET /api/news/image/{key}`.
- Готовая механика лайков/просмотров у видео: `video_likes`, `likes_count`,
  `views`, `VideoViewCounter` (Redis + батч-запись раз в 15 с), эндпоинты
  `POST/DELETE /api/videos/{id}/like`.

### iOS

- Те же DTO (`NewsDTO`), лента/деталь/создание; удаление и жалоба уже есть.
- `FullWidthImage` показывает фото с фиксированной высотой (тоже обрезает).

## 2. Проблемы, которые закрываем

1. **Фото обрезается.** В `item_news.xml` и `fragment_news_detail.xml`
   `ImageView` имеет фиксированную высоту (`180dp`/`250dp`) и
   `scaleType="centerCrop"` — при любом отличии пропорций часть кадра
   срезается.
2. **Нет реакций и статистики.** Нельзя лайкнуть новость, не видно
   просмотров/лайков/комментариев.
3. **Нет редактирования.** Автор может только удалить новость.
4. **Действия спрятаны внизу.** Кнопки удаления/жалобы — большие кнопки под
   текстом, не в стиле остального приложения (в чате действия открываются
   шторкой с иконками).
5. **Нет комментариев.** Обсуждения новостей отсутствуют.

## 3. UX и поведение

### 3.1. Фото

- Полное фото без обрезки: ширина = ширине карточки/экрана,
  высота по пропорциям изображения, `fitCenter`.
- Ограничение высоты, чтобы портретное фото не занимало весь экран:
  в ленте `maxHeight ≈ 280dp`, в детали `maxHeight ≈ 360dp`.
  Если пропорции не вписываются — фото уменьшается целиком, по бокам
  остаются поля цвета карточки (не обрезка).
- Одинаковое поведение в ленте, детали и превью редактора.
- Опционально (не в MVP): тап по фото открывает существующий полноэкранный
  просмотрщик (MEDIA.12).

### 3.2. Лента

В нижней части карточки — строка метрик:

```
♡ 12   👁 340   💬 5            автор · дата
```

- Сердечко: заливка при `likedByMe`, число лайков.
- Глаз: число просмотров.
- Комментарий: число комментариев.
- Тап по сердечку не открывает новость: оптимистично меняем состояние,
  затем подтверждаем ответом сервера; при ошибке откатываем и показываем
  Toast.
- Числа компактно: `999`, `1,2 тыс.`, `3,4 млн` (общий форматтер).

### 3.3. Деталь новости

Новая структура экрана:

```
[←]  Новость                                   [⋮]
┌──────────────────────────────────────────────┐
│ Фото (полностью, без обрезки)                 │
│ Заголовок                                     │
│ Автор · дата                                  │
│ ♡ 12   👁 340   💬 5                          │
│ [связанное видео, если есть]                  │
│ Текст новости                                 │
│ ── Комментарии (5) ─────────────────────────  │
│ [аватар] автор · 2 ч.                         │
│ комментарий                                   │
│ ...                                           │
└──────────────────────────────────────────────┘
[ Поле ввода комментария…                ] [→]
```

- Верхняя панель в едином стиле приложения: градиент `bg_toolbar_gradient`,
  кнопка «назад», заголовок, кнопка «⋮».
- «⋮» открывает нижнюю шторку действий с иконками (как у сообщений):
  - автор: «Редактировать», «Удалить»;
  - остальные: «Пожаловаться».
- Удаление — подтверждающий диалог, затем возврат в ленту.
- Редактирование открывает тот же экран, что создание, с заполненными
  полями и заголовком «Редактировать новость».
- Список — `RecyclerView` с `ConcatAdapter`: шапка-статья + комментарии;
  нижний композер закреплён, учитывает IME (через инсеты `MainActivity`).
- Pull-to-refresh обновляет новость и первый экран комментариев.

### 3.4. Комментарии

- Визуальный порядок: **старые сверху, новые снизу** (хронологическая лента).
- При открытии показываем последние 20 комментариев; над первым из них —
  строка «Показать более ранние», которая подгружает предыдущие 20 (и так
  далее). Экран открывается на статье, без принудительной прокрутки.
- Пагинация по 20 вверх (курсор `before`), прогресс-строка, пустое
  состояние «Пока нет комментариев».
- Композер: многострочное поле, лимит 2000 символов, кнопка отправки
  активна только при непустом тексте; на время отправки — прогресс,
  повторная отправка заблокирована.
- После успешной отправки комментарий сразу появляется в конце списка
  (новые снизу), счётчик увеличивается, поле очищается, список
  прокручивается к новому комментарию.
- Действия над комментарием — «⋮» в строке (или long-press) → та же шторка:
  - свой комментарий: «Удалить»;
  - чужой: «Пожаловаться» (если включаем жалобы на комментарии).
- Удаление подтверждается; оптимистичное удаление с откатом при ошибке.

## 4. Архитектура бэкенда (`makeup`)

### 4.1. Схема БД (Flyway `V11__news_engagement.sql`)

#### Что уже есть (до изменений)

```
users
  id, username(uq), auth_subject(uq), full_name, avatar_url, role,
  is_active, is_deleted, deleted_at, created_at, updated_at

news
  id, title, content, image_key, video_id → videos(id) ON DELETE SET NULL,
  author_id → users(id) ON DELETE RESTRICT, status, published_at,
  created_at, updated_at, deleted_at
  индексы: idx_news_published_at (partial), idx_news_author_id,
           idx_news_video_id, idx_news_title_trgm

content_reports
  report_id, report_uuid(uq), reporter_id → users(id), content_type
    CHECK IN ('NEWS','VIDEO'), content_id, target_user_id → users(id),
  reason, comment, content_preview, status, created_at, reviewed_at,
  reviewed_by
  uq(reporter_id, content_type, content_id)
```

Связи после изменений:

```
users 1 ────< news            author_id     RESTRICT
users 1 ────< news_comments   author_id     RESTRICT
users 1 ────< news_likes      user_id       CASCADE
news  1 ────< news_comments   news_id       CASCADE
news  1 ────< news_likes      news_id       CASCADE
news  >──── 1 videos          video_id      SET NULL
users 1 ────< content_reports reporter_id   (полиморфная ссылка на NEWS /
                                             VIDEO / NEWS_COMMENT без FK)
```

`content_reports.content_id` — полиморфный идентификатор: для `NEWS_COMMENT`
это `news_comments.id`, для `NEWS`/`VIDEO` — соответствующие id. Поэтому FK на
комментарий нет: таблица жалоб уже живёт по такой схеме.

#### Миграция

```sql
-- Счётчики новости. На PostgreSQL 11+ ADD COLUMN с DEFAULT не переписывает
-- таблицу; новости получают 0.
alter table news add column views bigint not null default 0;
alter table news add column likes_count bigint not null default 0;

-- Факт лайка. PK покрывает «лайкнул ли пользователь» и «все лайки юзера»;
-- отдельный индекс по news_id нужен для подсчёта и каскада.
create table news_likes (
    user_id    bigint      not null,
    news_id    bigint      not null,
    created_at timestamp   not null default now(),
    primary key (user_id, news_id),
    constraint fk_news_likes_user foreign key (user_id)
        references users (id) on delete cascade,
    constraint fk_news_likes_news foreign key (news_id)
        references news (id) on delete cascade
);
create index idx_news_likes_news on news_likes (news_id);

-- Комментарии. Мягкое удаление: строка остаётся для аудита/жалоб.
-- Курсор пагинации — id (монотонный), поэтому индекс (news_id, id desc).
create table news_comments (
    id         bigint generated by default as identity primary key,
    news_id    bigint        not null,
    author_id  bigint        not null,
    content    varchar(2000) not null,
    created_at timestamp     not null default now(),
    updated_at timestamp     not null default now(),
    deleted_at timestamp,
    constraint fk_news_comments_news foreign key (news_id)
        references news (id) on delete cascade,
    constraint fk_news_comments_author foreign key (author_id)
        references users (id) on delete restrict,
    constraint ck_news_comments_content_not_blank
        check (btrim(content) <> '')
);
-- Партиальный индекс: и список, и count читают только неудалённые.
create index idx_news_comments_news_id
    on news_comments (news_id, id desc)
    where deleted_at is null;
create index idx_news_comments_author on news_comments (author_id);

-- Жалобы на комментарии: расширяем допустимый тип контента.
alter table content_reports
    drop constraint if exists content_reports_content_type_check;
alter table content_reports
    add constraint content_reports_content_type_check
    check (content_type in ('NEWS', 'VIDEO', 'NEWS_COMMENT'));
```

#### Инварианты

- `news.likes_count` — денормализация `count(news_likes)`; меняется в одной
  транзакции с insert/delete лайка (`+1` только при реальной вставке, `-1`
  только при реальном удалении). PK защищает от двойного лайка.
- `news.views` — приблизительный счётчик (Redis-батч, отставание до 15 с),
  не участвует в правах и бизнес-логике.
- `commentsCount` не хранится в `news`: считается батчем по странице ленты
  (`count ... where news_id in (...) and deleted_at is null`), чтобы не
  делать UPDATE на каждый комментарий.
- `news_comments.deleted_at` — soft delete. Список и счётчик фильтруют
  `deleted_at is null`, строка остаётся для аудита и уже отправленных жалоб.
- Пользователи физически не удаляются (`users.is_deleted` + `is_active`),
  поэтому `RESTRICT` на авторов не мешает удалению аккаунта; имя скрывается
  через `UserDisplay.DELETED`.
- При жёстком удалении новости каскадом уходят комментарии и лайки; при
  мягком (`news.deleted_at`) они остаются, но API новости уже не отдаёт.

#### Запросы под индексы

| Сценарий | Запрос | Индекс |
|---|---|---|
| Лента | `views, likes_count` в проекции + `count(comments)` батчем | `idx_news_published_at` |
| Мои лайки | `select news_id from news_likes where user_id = ?` | PK `(user_id, news_id)` |
| Последние комментарии | `where news_id=? and deleted_at is null order by id desc limit 20` | `idx_news_comments_news_id` |
| Более ранние | то же + `and id < ?` | `idx_news_comments_news_id` |
| Все комментарии автора | `where author_id=?` | `idx_news_comments_author` |
| Жалобы на комментарий | `content_type='NEWS_COMMENT' and content_id=?` | `idx_content_reports_content` |

#### Проверка и откат

- Сверка денормализации (мониторинг/ручная проверка):
  ```sql
  select n.id, n.likes_count, count(l.*) as actual
  from news n left join news_likes l on l.news_id = n.id
  group by n.id, n.likes_count
  having n.likes_count <> count(l.*);
  ```
- Откат (только до появления данных `NEWS_COMMENT`):
  ```sql
  drop table if exists news_comments;
  drop table if exists news_likes;
  alter table news drop column if exists likes_count;
  alter table news drop column if exists views;
  alter table content_reports
      drop constraint if exists content_reports_content_type_check;
  alter table content_reports
      add constraint content_reports_content_type_check
      check (content_type in ('NEWS', 'VIDEO'));
  ```

### 4.2. Модель и проекции

- `NewsItem` + поля `views`, `likesCount`.
- `NewsLike` (составной ключ `user_id + news_id`), `NewsLikeRepository`.
- `NewsComment` + `NewsCommentRepository`:
  - курсорная выборка по `newsId` c `deletedAt is null`:
    `id < before` (или без `before` — последние), сортировка `id desc`,
    затем разворот в `id asc` для ответа;
  - `countByNewsId` / `countByNewsIdIn` для счётчика без N+1;
  - удаление — автор комментария или автор новости.
- Проекция `NewsCommentView` (id, newsId, content, createdAt,
  authorUsername, authorDeleted) — без загрузки `User` целиком.
- `NewsListItem` и `NewsResponse` получают `views`, `likesCount`,
  `commentsCount`.
- `likedByMe` в **ленте не отдаём**: `/api/news` кешируется nginx общим
  ключом, персонализация сломает кеш. Клиент получает свои лайки отдельным
  запросом (см. 4.3). В детали `likedByMe` возвращаем — она не кешируется.

### 4.3. API

| Метод | Путь | Авторизация | Назначение |
|---|---|---|---|
| GET | `/api/news` | нет | Лента: + `views`, `likes`, `commentsCount` |
| GET | `/api/news/{id}` | нет | Деталь: + `views`, `likes`, `commentsCount`, `likedByMe` |
| POST | `/api/news/{id}/like` | да | Поставить лайк, идемпотентно |
| DELETE | `/api/news/{id}/like` | да | Снять лайк, идемпотентно |
| GET | `/api/news/likes/me` | да | `{ "likedIds": [1,2] }` — все лайки пользователя |
| POST | `/api/news/{id}/view` | нет* | +1 просмотр (зовётся клиентом один раз при открытии) |
| PUT | `/api/news/{id}` | да, автор | Редактирование (multipart) |
| GET | `/api/news/{newsId}/comments?before=&size=` | нет | Комментарии: последние 20 или более старые до `before`, в ответе по возрастанию |
| POST | `/api/news/{newsId}/comments` | да | Добавить комментарий |
| DELETE | `/api/news/{newsId}/comments/{commentId}` | да, автор комментария / автор новости | Удалить (мягко) |

\* `POST /view` без токена тоже работает: счётчик не привязан к пользователю
в MVP.

Форматы:

- Лайк: `{ "liked": true, "likes": 7 }`.
- Страница комментариев:
  ```json
  {
    "comments": [
      {
        "id": 10,
        "newsId": 5,
        "content": "Текст",
        "author": "dabuldakov",
        "createdAt": "2026-11-25T10:00:00"
      }
    ],
    "total": 42,
    "hasMore": true,
    "nextBefore": 10
  }
  ```
  `comments` всегда по возрастанию `id`; без `before` — последние 20,
  `hasMore` — есть ли более старые, `nextBefore` — курсор для следующей
  страницы вверх. У деактивированного автора
  `author = "Удалённый пользователь"` через `UserDisplay`.
- Комментарий создаётся тем же DTO без `hasMore`/`nextBefore` (или в
  обёртке `{ "comment": {...}, "total": 43 }`).
- Ошибки — существующий `GlobalExceptionHandler` (404/403/400).

`PUT /api/news/{id}` (multipart):

- `title`, `content` — обязательны (те же ограничения, что при создании);
- `videoId` — опционально;
- `image` — новое фото;
- `removeImage=true` — удалить текущее фото.

Логика: проверка автора → обновление полей → новое фото в MinIO →
старый ключ в `StorageCleanupService` **после коммита** → ответ с полными
счётчиками. `publishedAt` не меняется, `updatedAt` обновляется автоматически.

### 4.4. Просмотры

- Новый `NewsViewCounter` по образцу `VideoViewCounter`:
  Redis-хеш `makeup::news:views:pending`, flush раз в 15 с
  (`UPDATE news SET views = views + ? WHERE id = ?`), лок на flush,
  fallback на синхронный UPDATE при недоступном Redis.
- Инкремент только из `POST /api/news/{id}/view`, чтобы правки и повторные
  `GET` (например, после редактирования) не накручивали счётчик.
- Клиент вызывает один раз на открытие экрана, не при refresh/повороте.
- MVP: каждый просмотр учитывается (как у видео). Уникальность
  «пользователь + новость + окно времени» — отдельный этап, если понадобится.

### 4.5. Лайки

- `POST` — добавить при отсутствии, `DELETE` — удалить при наличии.
  В отличие от видео, без «toggle на оба метода»: повторный запрос не
  переворачивает состояние.
- `likes_count` меняется в той же транзакции; ответ — актуальное число.
- Гонки двух устройств: PK `(user_id, news_id)` + обработка
  `DataIntegrityViolationException` (повторный POST = успех).

### 4.6. Комментарии: правила

- Только к опубликованной, не удалённой новости; иначе 404.
- Текст: `trim`, 1..2000 символов; пустой — 400.
- Автор комментария может удалить свой; автор новости может удалять любые
  комментарии под своей новостью (модерация). Админ — через общий механизм,
  если появится.
- Редактирование комментариев в MVP **не делаем** (можно добавить позже без
  изменения схемы).
- Уведомления автору новости о комментарии — отдельный этап (push живёт в
  сервисе `chat`).

### 4.7. Жалобы на комментарии (включено в задачу)

- Расширить `content_reports.content_type`: `NEWS`, `VIDEO`, `NEWS_COMMENT`.
- В `ContentReportService` добавить ветку `NEWS_COMMENT`: найти комментарий,
  запретить жалобу на свой, `target_user_id` = автор комментария,
  `content_preview` = первые 500 символов.
- На Android переиспользовать `ReportDialog` и `reportContent`.
- Жалоба на комментарий отправляется из шторки действий комментария;
  после успеха показываем «Жалоба отправлена».

### 4.8. Инфраструктура

- Лимиты в `RateLimitFilter`:
  - `POST /api/news/{id}/like`, `DELETE /api/news/{id}/like` — 60/мин;
  - `POST /api/news/{id}/view` — 120/мин;
  - `POST /api/news/{id}/comments` — 20/мин;
  - `PUT /api/news/{id}` — 10/мин.
- nginx: `location ~ ^/api/news/[0-9]+$` с `client_max_body_size 25m`
  (multipart-редактирование), `location ~ ^/api/news/[0-9]+/comments$`
  без кеша. `GET /api/news/likes/me` тоже не кешировать (персональный).
- `GET /api/news/{id}` не кешируется (уже идёт через `location /`).
- `commentsCount` считаем батчем на страницу ленты (`countByNewsIdIn`),
  без денормализации и лишних UPDATE на каждый комментарий.
- ArchUnit: всё остаётся внутри модуля `news`; при жалобах на комментарии
  `report → news` — направление уже существует и ациклично.

## 5. Архитектура Android

### 5.1. Слой данных

- `NewsResponse`: `views: Long?`, `likes: Long?`, `commentsCount: Int?`,
  `likedByMe: Boolean?` (nullable — совместимость со старым бэкендом).
- Новые DTO: `NewsCommentResponse`, `NewsLikeResponse`, `LikedNewsResponse`.
- Домен `News` дополняется теми же полями; новый `NewsComment`.
- `ApiService`:
  ```kotlin
  @POST("/api/news/{id}/like") suspend fun likeNews(...): Response<NewsLikeResponse>
  @DELETE("/api/news/{id}/like") suspend fun unlikeNews(...): Response<NewsLikeResponse>
  @GET("/api/news/likes/me") suspend fun getLikedNewsIds(): Response<LikedNewsResponse>
  @POST("/api/news/{id}/view") suspend fun registerNewsView(...): Response<Unit>
  @PUT("/api/news/{id}") @Multipart suspend fun updateNews(...): Response<NewsResponse>
  @GET("/api/news/{id}/comments") suspend fun getNewsComments(...): Response<PageResponse<NewsCommentResponse>>
  @POST("/api/news/{id}/comments") suspend fun addNewsComment(...): Response<NewsCommentResponse>
  @DELETE("/api/news/{id}/comments/{commentId}") suspend fun deleteNewsComment(...): Response<Unit>
  ```
- `NewsRepository` + `NewsRepositoryImpl`: те же операции, `Resource<...>`.
- Мапперы DTO → домен; отсутствующие поля → `0`/`null`.

### 5.2. Лента

- `NewsViewModel`:
  - после загрузки страницы — запрос `getLikedNewsIds()` и merge в элементы;
  - `toggleLike(newsId)` с оптимистичным обновлением `_feedState`;
  - защита от двойного тапа (in-flight set), откат при ошибке.
- `NewsAdapter`:
  - колбэки `onItemClick`, `onLikeClick`;
  - `DiffUtil` уже сравнивает `News` целиком — новые поля попадут в diff;
  - отдельный `LikeButton`/`ImageButton` с `contentDescription` и состоянием
    `selected` (заливка/контур).
- `item_news.xml`:
  - фото: `adjustViewBounds=true`, `scaleType=fitCenter`, `maxHeight`;
  - строка метрик с иконками `ic_heart_outline`, `ic_heart_filled`,
    `ic_eye`, `ic_comment`;
  - автор/дата остаются, но переносятся в отдельную строку при нехватке места.
- Форматтер чисел — `utils/CountFormatter.kt` + unit-тесты.

### 5.3. Деталь

- Новый `NewsDetailViewModel` (вместо раздувания `NewsViewModel`):
  - `news: StateFlow<News?>`, загрузка/ошибка;
  - `comments: StateFlow<CommentsUiState>` (items по возрастанию,
    `nextBefore`, `hasMore`, `total`, isLoading, isSending, error);
  - `load(newsId)` — новость + последние 20 комментариев;
  - `loadOlderComments()` (по `nextBefore`) — подгрузка более ранних вверх
    с сохранением позиции прокрутки;
  - `addComment(text)`, `deleteComment(id)`, `toggleLike()`, `deleteNews()`;
  - `registerView()` — один раз на `newsId` в пределах ViewModel.
- `NewsDetailFragment`:
  - `fragment_news_detail.xml` → вертикальный `LinearLayout`:
    градиентный тулбар, `SwipeRefreshLayout` с `RecyclerView` (weight=1),
    композер комментария;
  - `ConcatAdapter(NewsHeaderAdapter, NewsCommentsAdapter, FooterAdapter)`;
  - шапка — отдельный `item_news_header.xml` (фото, заголовок, метрики,
    видео, текст, заголовок «Комментарии»);
  - комментарий — `item_news_comment.xml` (аватар, автор, время, текст, «⋮»);
  - `NewsActionsBottomSheet` — шторка с иконками в стиле
    `MessageActionsBottomSheet` (edit/delete/report);
  - удаление/редактирование/жалоба — существующие строки и диалоги;
  - после удаления — `popBackStack()`;
  - после редактирования — перезагрузка новости без инкремента просмотра;
  - скролл к комментариям после отправки, авто-скрытие клавиатуры по желанию.
- `CreateNewsActivity`:
  - режим `create`/`edit` (`EXTRA_NEWS_ID`);
  - при edit: prefill title/content/video, показ текущего фото, кнопка
    «Удалить фото»;
  - submit → `updateNews`; успех → `setResult(RESULT_OK)` и `finish()`;
  - заголовок и кнопка меняются на «Редактировать»/«Сохранить».
- Состояние ленты после возврата: `onResume` уже вызывает `refreshNews()`;
  дополнительно можно передавать результат, чтобы не ждать сеть.

### 5.4. Ресурсы

- Новые drawable: `ic_heart_outline`, `ic_heart_filled`, `ic_eye`,
  `ic_comment`, `ic_more_vert`.
- Строки: «Комментарии», «Пока нет комментариев», «Написать комментарий…»,
  «Редактировать новость», «Сохранить», «Удалить комментарий», «Жалоба
  отправлена» и т.д. (RU + EN, как принято в проекте).
- Тёмная тема: цвета иконок через `?attr/colorOnSurface` /
  `?attr/colorPrimary`, без хардкода.

## 6. iOS (если делаем в этой же итерации)

- DTO: `views`, `likes`, `commentsCount`, `likedByMe`, `NewsComment`.
- `NewsRepository`: лайк, просмотр, комментарии, редактирование.
- `NewsListView`: метрики + сердечко.
- `NewsDetailView`: `ScrollView` → `List`/`LazyVStack` с шапкой и
  комментариями, композер снизу (`.safeAreaInset(edge: .bottom)`),
  тулбар с меню действий.
- `FullWidthImage`: `scaledToFit` + ограничение высоты вместо `scaledToFill`,
  чтобы фото не обрезалось (общий компонент затронет и другие экраны —
  проверить регрессии).

## 7. Тесты

### Бэкенд

- Unit:
  - `NewsServiceTest`: лайк/анлайк (в т.ч. повторный), просмотр, права на
    редактирование, редактирование с заменой/удалением фото, комментарии
    (создание/удаление/права/валидация).
  - `NewsMapperTest`: новые поля, `commentsCount`, удалённый автор.
- Integration (`NewsControllerIT`):
  - лайки: 200/403, счётчик, повторный POST идемпотентен;
  - просмотры: `POST /view` увеличивает `views`;
  - `PUT /api/news/{id}`: автор/не автор/404; замена фото;
  - комментарии: список, порядок, пагинация, создание без токена → 403,
    удаление своим/чужим/автором новости, пустой текст → 400;
  - `GET /api/news` отдаёт счётчики.
- ArchUnit + JaCoCo (порог 70%).

### Android

- `CountFormatterTest`.
- `NewsRepositoryImplTest` / `NewsViewModelTest`: лайк оптимистичный,
  откат при ошибке, merge `likedByMe`.
- `NewsAdapterTest`: привязка счётчиков, клик по сердечку, `selected`.
- Новый `NewsDetailViewModelTest`: пагинация комментариев, добавление,
  удаление, ошибки, однократный `registerView`.
- `NewsDetailFragmentTest` (Robolectric+Hilt): новая разметка, видимость
  действий для автора/не автора, комментарии, композер.
- `CreateNewsActivityTest`: режим редактирования, prefill, submit.
- Существующие тесты, ссылающиеся на `btnDeleteNews`/`btnReportNews`,
  переписать.

## 8. Этапы

| Этап | Что | Результат |
|---|---|---|
| 0 | Согласовать план и контракт | этот документ |
| 1 | Бэкенд: миграция, счётчики, лайки, просмотры | API готово, тесты |
| 2 | Android: фото без обрезки + метрики/лайки в ленте | видимый результат |
| 3 | Бэкенд + Android: редактирование новости | автор может править |
| 4 | Бэкенд: комментарии (+ жалобы, если включаем) | API готово |
| 5 | Android: деталь на RecyclerView, шторка действий, комментарии | полный UX |
| 6 | iOS (опционально) | паритет |
| 7 | Тесты, `FEATURE_CATALOG`, `WORKLOG`, beta-APK | релиз-кандидат |

Ориентир: этапы 1–5 — около 5–7 рабочих дней; iOS — ещё 2–3 дня.

## 9. Совместимость и риски

- Все поля новые и nullable — старые клиенты и старый бэкенд не ломаются.
- `likedByMe` не попадает в кешируемую ленту — иначе nginx отдаст чужие
  лайки.
- Просмотры без дедупликации в MVP: счётчик может завышаться при частых
  открытиях. Если критично — Redis-дедуп по `user/news` с TTL.
- Фото без обрезки меняет высоту карточек ленты: возможны «прыжки» при
  загрузке. Смягчение — `placeholder` с фиксированной высотой и
  `adjustViewBounds` после загрузки; в перспективе — отдавать width/height
  с сервера.
- Комментарии — новый UGC-поток: нужны лимиты, жалобы и модерация, иначе
  риск для Google Play. Жалобы на комментарии рекомендую включить сразу.
- Мягко удалённые комментарии не показываем, но счётчик считает только
  `deletedAt is null`.
- `PUT` с фото требует поднять `client_max_body_size` в nginx именно для
  пути новости.

## 10. Принятые решения (2026-11-25)

1. **Порядок комментариев:** старые сверху, новые снизу. При открытии —
   последние комментарии, старые подгружаются вверх по курсору `before`.
2. **Удаление комментария:** автор комментария + автор новости.
3. **Жалобы на комментарии:** включаем сразу, тип `NEWS_COMMENT`.
4. **Редактирование новости:** заголовок, текст, фото (замена/удаление) и
   связанное видео.
5. **iOS:** после Android, отдельным этапом; API с самого начала обратно
   совместимый.
