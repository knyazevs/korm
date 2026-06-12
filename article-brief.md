# Kormium — контекст для написания статей

Этот документ — бриф для авторов (людей и ИИ-агентов), пишущих статьи о Kormium.
Он содержит факты о проекте, честные плюсы и минусы, сравнение с конкурентами и
список утверждений, которые делать НЕЛЬЗЯ. Статьи должны быть объективными:
завышенные обещания вредят доверию к молодому проекту сильнее, чем честно
названные ограничения.

---

## 1. Что такое Kormium (elevator pitch)

**Kormium — это type-safe ORM и SQL DSL для Kotlin Multiplatform.**

Таблицы, сущности, типизированные предикаты, транзакции, джойны и агрегации
описываются один раз на Kotlin и работают на JVM и Kotlin/Native. API похож на
Exposed, но ядро портируемое: PostgreSQL доступен на JVM (JDBC/HikariCP), на
Kotlin/Native (libpq, без JDBC) и асинхронно через r2dbc; SQLite — на JVM,
Native, Android и iOS.

- Сайт/репозиторий: https://github.com/kormium/kormium
- Maven Central: группа `io.github.kormium`, есть BOM (`kormium-bom`)
- Лицензия: Apache 2.0
- Текущая версия: 0.5.0 (июнь 2026), статус **pre-1.0**
- Требования: JDK 21+ для JVM (suspend-офлоад на виртуальные потоки), Kotlin 2.4.x
- Тестируется на PostgreSQL 16; Android minSdk 24

### Пример кода (визитная карточка)

```kotlin
object App : Catalog

object Users : Table<App, User>("users", ::User) {
    val id by Column.UUID().primaryKey()
    val name by Column.Text()
    val age by Column.Int()
}

class User : Entity() {
    var id by Users.id
    var name by Users.name
    var age by Users.age
}

val db: Database<App> = createDatabase(host = "localhost", database = "postgres",
    user = "postgres", password = "password")

val adults = db.autocommit {
    Users.find {
        where { Users.age gtEq 18 }
        orderBy DESC Users.age
        limit = 50
    }
}
```

---

## 2. Структура проекта (модули)

| Модуль | Назначение |
| --- | --- |
| `kormium-core` | DSL, модель таблиц/сущностей, рендеринг SQL, скоупы — без зависимостей на конкретный бэкенд |
| `kormium-postgres` | PostgreSQL: JDBC/HikariCP на JVM, libpq на Native |
| `kormium-sqlite` | SQLite: sqlite-jdbc (JVM), sqlite3 cinterop (Native/iOS), AndroidX SQLite (Android) |
| `kormium-r2dbc` | Настоящий async PostgreSQL (`SuspendDatabase`), только JVM |
| `kormium-observe` | Реактивные `Flow`-запросы (аналог Room): запрос переэмитится при изменении читаемых таблиц |
| `kormium-migrate` | Раннер raw-SQL миграций: checksum-валидация, advisory lock на Postgres, журнал `kormium_migrations` |
| `kormium-jdbc` | Общий JVM-слой: пул, именованные параметры |
| `kormium-ktor`, `-ktor-di`, `-ktor-koin` | Интеграция с Ktor: без DI, со встроенным DI, с Koin |
| `kormium-bom` | BOM для выравнивания версий |

Платформы: JVM (основная), Linux Native, macOS Native (x64+arm64), Android и iOS
(только SQLite-бэкенд). Windows Native и Wasm — пока нет.

---

## 3. Сильные стороны (что честно можно подчёркивать)

1. **Единственный в своём классе KMP ORM с PostgreSQL на Kotlin/Native.**
   Exposed — JVM-only. SQLDelight ориентирован на мобильный SQLite. Kormium даёт
   серверный PostgreSQL через libpq без JVM — можно писать нативные
   Kotlin-сервисы и CLI-утилиты с Postgres. Это главный технический дифференциатор.

2. **Catalog-типобезопасность на уровне компилятора.** `Catalog` — фантомный тип:
   `Table<App, User>` нельзя использовать внутри `Database<Cache>` — компилятор
   отловит до рантайма. Полезно при нескольких БД (основная + кэш, шардинг).
   У конкурентов такого механизма нет.

3. **Блокирующий и suspend API как равноправные граждане.**
   `transaction {}` / `autocommit {}` для блокирующего кода,
   `suspendTransaction {}` / `suspendAutocommit {}` для корутин. На JVM
   блокирующая работа в suspend-пути офлоадится на виртуальные потоки (JDK 21).
   r2dbc-бэкенд — честный async без скрытого пула блокирующих потоков.

4. **Реактивные запросы как в Room, но мультиплатформенно.** `kormium-observe`
   превращает запрос в `Flow`, который переэмитится при коммите записи в читаемые
   таблицы. Это паритет с Room для Compose Multiplatform / Android UI, которого
   нет у Exposed.

5. **Честная семантика частичных обновлений.** Неприсвоенное поле ≠ `null`:
   отсутствующее поле просто не попадает в `INSERT`/`UPDATE` (работают дефолты БД
   и generated-колонки), а явный `null` пишется как SQL `NULL`. Есть `isSet` /
   `unset`. У большинства ORM это болевая точка.

6. **Открытая система типов колонок.** `ColumnType<T>` расширяем: встроенные
   типы, `enum`, `json`, кастомные конвертеры — без форка библиотеки.

7. **Продуманные миграции для продакшена.** Raw-SQL миграции с checksum
   (правка применённой миграции — fail fast), advisory lock на Postgres против
   двойного применения при параллельном старте инстансов, журнал с
   `applied_at` и порядком применения. Вся пачка — в одной транзакции.

8. **SQL не прячется.** Значения всегда биндятся параметрами (SQL-инъекции через
   DSL невозможны), но raw SQL — легальный escape hatch для DDL и
   backend-специфики. Философия: пользователь должен понимать таблицы, индексы и
   транзакции.

9. **Производительность на уровне.** По собственным JMH-бенчмаркам JVM-бэкенд
   сопоставим с Exposed; в 0.5.0 типизированный биндинг параметров Postgres
   ускорил чтения в ~1.7–2x (убран лишний round-trip протокола), что по wire-трейсу
   закрыло разрыв с Hibernate по чтениям. Native-бэкенд (libpq) на чтениях
   быстрее JVM-варианта (~13k vs ~8k ops/s в 8 потоков). Все цифры — относительные,
   «прогоните на своём железе».

10. **Инфраструктура взрослого проекта.** Maven Central + BOM, CI с
    Native-тестами, подробные docs (production guide, observability, compatibility
    policy, cookbook), runnable-сэмплы (Ktor CRUD, шардинг, SQLite-кэш перед
    Postgres, r2dbc), changelog по Keep a Changelog / SemVer, бенчмарки против
    Exposed и Hibernate в репозитории.

---

## 4. Слабые стороны и ограничения (обязательны к учёту)

1. **Pre-1.0: API нестабилен.** Публичный API может меняться между минорными
   версиями. В 0.4.0 был полный ребрендинг артефактов (`korm-*` → `kormium-*`),
   в 0.5.0 — три breaking changes. Сам проект пишет: «не делайте его единственным
   слоем персистентности критичных продакшен-систем».

2. **Kormium не управляет схемой.** Нет генерации `CREATE TABLE` из определений
   таблиц — схему создаёте raw SQL или миграциями. Это осознанное дизайн-решение,
   но для людей, привыкших к `SchemaUtils.create()` (Exposed) или Hibernate
   `hbm2ddl`, — минус по удобству. Нет first-class метаданных индексов и
   foreign key.

3. **Только два диалекта: PostgreSQL и SQLite.** Нет MySQL/MariaDB, Oracle,
   SQL Server, H2. Для многих enterprise-команд это сразу стоп-фактор.

4. **Дублирование описаний.** Колонка объявляется и в `Table`, и в `Entity`
   (делегаты). Нет кодогенерации или compiler plugin (это в exploratory-части
   роадмапа). Сущности — мутабельные классы, наследующие `Entity`, а не
   иммутабельные data classes (SQLDelight здесь эргономичнее).

5. **Неполное покрытие SQL.** Джойны, агрегации и `HAVING` есть, но покрытие
   расширяется; роадмап прямо требует «документировать неподдерживаемые
   SQL-фичи, а не подразумевать полное покрытие». Сложные подзапросы, CTE,
   window functions — не заявлены. Escape hatch — raw SQL.

6. **Высокий порог по окружению.** JDK 21+ обязателен для JVM (виртуальные
   потоки), Kotlin 2.4.x. Команды на JDK 11/17 отсекаются.

7. **Postgres-бэкенда нет на Android и iOS** — там только SQLite. Windows Native
   и Wasm отсутствуют.

8. **Маленькое сообщество.** Молодой проект: нет массива ответов на Stack
   Overflow, статей, готовых рецептов, плагинов IDE. Экосистема интеграций —
   только Ktor (нет Spring, Micronaut, Quarkus).

9. **Миграции — только raw SQL** (с 0.4.0 убрана Kotlin-lambda форма). Это
   плюс для контроля, но нет typed-DSL миграций и автогенерации diff схемы
   (как у Flyway это норм, но Liquibase/Exposed-подходы дают больше).

10. **Бенчмарки — собственные.** Независимых замеров нет; цифры из репозитория
    надо подавать с оговоркой «по бенчмаркам самого проекта».

---

## 5. Сравнение с конкурентами (для объективности)

| | Kormium | Exposed (JetBrains) | SQLDelight | Hibernate/JPA | Room |
| --- | --- | --- | --- | --- | --- |
| Платформы | JVM + Native + Android/iOS (SQLite) | только JVM | KMP (мобильный фокус) | только JVM | только Android/KMP |
| PostgreSQL без JVM | **да (libpq)** | нет | нет | нет | нет |
| Подход | Kotlin DSL → SQL | Kotlin DSL → SQL | SQL → кодоген Kotlin | аннотации/JPQL | аннотации + SQL |
| Реактивные Flow-запросы | да (observe) | нет | да | нет | да |
| Compile-time изоляция БД (Catalog) | **да** | нет | нет | нет | нет |
| Зрелость/сообщество | низкая | высокая | высокая | очень высокая | очень высокая |
| Диалекты | Postgres, SQLite | много | SQLite, Postgres, MySQL... | очень много | SQLite |
| Управление схемой | нет (raw SQL) | да | да (из .sq) | да | да |

Честное позиционирование: **Kormium — это «Exposed для Kotlin Multiplatform»
с Room-подобной реактивностью и уникальным Postgres-на-Native**, но моложе,
с меньшим покрытием SQL и без управления схемой.

---

## 6. Кому подходит / сценарии для статей

Хорошие сценарии (тут Kormium силён):
- Ktor-сервис на PostgreSQL с typed DSL и suspend API (есть готовые сэмплы с DI/Koin);
- Kotlin/Native CLI или микросервис с Postgres **без JVM** — уникальная ниша;
- Compose Multiplatform / KMP-приложение: SQLite + реактивные Flow-запросы,
  общий код персистентности для Android/iOS/desktop;
- SQLite как кэш перед PostgreSQL с compile-time разделением каталогов;
- шардинг: один `Catalog`, несколько `Database`-инстансов.

Плохие сценарии (не продвигать):
- enterprise на MySQL/Oracle/SQL Server;
- команды на JDK < 21;
- критичные продакшен-системы, где недопустимы breaking changes до 1.0;
- проекты, ожидающие автогенерацию схемы и rich-mapping уровня Hibernate
  (lazy-коллекции, кэш 2-го уровня, dirty checking).

---

## 7. Чего НЕЛЬЗЯ утверждать в статьях

- ❌ «production-ready» — проект сам говорит pre-1.0; можно: «готовится к 1.0,
  фокус на надёжности».
- ❌ «самый быстрый ORM» — бенчмарки собственные и «indicative».
- ❌ «полная поддержка SQL» — покрытие неполное, проект это явно признаёт.
- ❌ «замена Hibernate» — другой класс инструмента и зрелости.
- ❌ «работает везде» — Windows Native и Wasm не поддерживаются, Postgres нет
  на Android/iOS.
- ❌ Не скрывать требование JDK 21+ и ограничение в два диалекта.

Тон статей: инженерный, конкретный, с кодом и честными оговорками. Сильная
сторона проекта — честность его собственной документации; статьи должны её
наследовать, а не противоречить ей.

---

## 8. Идеи углов подачи (article angles)

1. «PostgreSQL из Kotlin/Native без JVM и JDBC» — про libpq-бэкенд, ниша без конкурентов.
2. «Фантомные типы на практике: как Catalog ловит обращение не к той базе на компиляции».
3. «Room-подобные Flow-запросы в Kotlin Multiplatform» — kormium-observe + Compose MP.
4. «Absent vs null: как Kormium решает проблему частичных UPDATE».
5. «Как мы ускорили чтения Postgres в 2 раза одним типизированным биндингом» —
   разбор фикса 0.5.0 (untyped text params → лишний round-trip протокола).
6. «Миграции, которые не выстрелят в ногу: checksum + advisory lock».
7. Сравнение «Kormium vs Exposed vs SQLDelight: что выбрать для KMP в 2026».
8. «Виртуальные потоки JDK 21 как мост между блокирующим JDBC и корутинами».
