# Google Play listing

Adapted from the iOS copy (`regional-check` `docs/operations/app-store-copy.md`, the 3.0 promotional
text and the proposed English description), for what this build ships. The adaptations:
- CarPlay becomes Android Auto;
- the Live Activity becomes the alert notification;
- Pro is dropped.

Android Auto is named because 1.0 ships its car screen ([spec 004](../004-android-auto/spec.md)); CarPlay and Android Automotive OS are never named.
`scripts/listing-lengths.py` checks every field against Play's limits.

## Fields

| Field | Limit | Value |
|---|---|---|
| App name | 30 | DriveCheckUA |
| Category | | Tools |
| Tags | | (owner's choice in Play Console) |
| Contact email | | the owner's developer email (Play Console account) |
| Website | | https://github.com/vil4max/drivecheck-android |
| Privacy policy | | https://vil4max.github.io/drivecheck-android/privacy-policy.html (GitHub Pages serves `main` `/docs`; live once this slice merges) |

## en-US

### Short description

```
Your region's air raid alert status at a glance, with a widget and a tile.
```

### Full description

```
Drive Check answers one question at a glance: is your region of Ukraine under an air raid alert?

Open the app, see whether your current region is under alert, and get back to the road. The status is glanceable: not a live map to navigate by, not a notification feed, and it does not track you in the background.

What you get
• The current region's status, with the time it was checked
• Android Auto: your region's status on the car screen, with Refresh
• A region that follows your location: nothing to pick or pin
• Alerts in neighbouring regions, shown with your own status
• The alert map as a reference image, loaded on demand and never on a timer; tap it for every region's status
• A lock-screen notification while your region is under alert, once the app or the widget sees it, until the all-clear is seen. Not a push alert service
• A home-screen widget with Refresh
• A quick-settings tile

Important
Alert data is informational only. Do not rely on it for critical safety decisions, and do not use the map for navigation: it is a reference image, not turn-by-turn guidance. Always follow official instructions and local authorities.

Privacy
Location is used only while the app is open, to find your region, and it is never sent to the developer or to the alert data provider. No accounts. No ads. No third-party analytics.

Alert data: Ubilling aerial alerts (ubilling.net.ua).
```

## uk

### Short description

```
Статус повітряної тривоги у вашому регіоні з одного погляду. Віджет і плитка.
```

### Full description

```
Drive Check відповідає на одне питання з одного погляду: чи оголошена повітряна тривога в регіоні України, де ви зараз перебуваєте?

Відкрийте застосунок, подивіться, чи є тривога у вашому регіоні, і повертайтеся до дороги. Це статус з одного погляду: не карта для навігації, не стрічка сповіщень, і застосунок не стежить за вами у фоні.

Що є
• Статус вашого регіону і час перевірки
• Android Auto: статус вашого регіону на екрані авто, з кнопкою «Оновити»
• Регіон за геолокацією: нічого не треба вибирати
• Тривоги в сусідніх регіонах поруч із вашим статусом
• Карта тривог як довідкове зображення: завантажується на вимогу, ніколи за таймером; торкніться її, щоб побачити статус усіх регіонів
• Сповіщення на екрані блокування, поки у вашому регіоні тривога: з'являється, коли застосунок або віджет її побачить, і тримається до відбою. Це не служба push-сповіщень
• Віджет на головному екрані з кнопкою «Оновити»
• Плитка у швидких налаштуваннях

Важливо
Дані про тривоги мають лише довідковий характер. Не покладайтеся на них у критичних рішеннях щодо безпеки і не використовуйте карту для навігації. Завжди дотримуйтеся офіційних інструкцій і вказівок місцевої влади.

Приватність
Геолокація використовується лише поки застосунок відкритий, щоб визначити регіон, і ніколи не надсилається розробнику чи постачальнику даних. Без акаунтів. Без реклами. Без сторонньої аналітики.

Дані про тривоги: Ubilling aerial alerts (ubilling.net.ua).
```

## ru

### Short description

```
Статус воздушной тревоги в вашем регионе с одного взгляда. Виджет и плитка.
```

### Full description

```
Drive Check отвечает на один вопрос с одного взгляда: объявлена ли воздушная тревога в регионе Украины, где вы сейчас находитесь?

Откройте приложение, посмотрите, есть ли тревога в вашем регионе, и возвращайтесь к дороге. Это статус с одного взгляда: не карта для навигации, не лента уведомлений, и приложение не следит за вами в фоне.

Что есть
• Статус вашего региона и время проверки
• Android Auto: статус вашего региона на экране авто, с кнопкой «Обновить»
• Регион по геолокации: ничего не нужно выбирать
• Тревоги в соседних регионах рядом с вашим статусом
• Карта тревог как справочное изображение: загружается по запросу, никогда по таймеру; коснитесь её, чтобы увидеть статус всех регионов
• Уведомление на экране блокировки, пока в вашем регионе тревога: появляется, когда приложение или виджет её увидит, и держится до отбоя. Это не служба push-уведомлений
• Виджет на главном экране с кнопкой «Обновить»
• Плитка в быстрых настройках

Важно
Данные о тревогах носят только справочный характер. Не полагайтесь на них в критических решениях о безопасности и не используйте карту для навигации. Всегда следуйте официальным инструкциям и указаниям местных властей.

Конфиденциальность
Геолокация используется только пока приложение открыто, чтобы определить регион, и никогда не отправляется разработчику или поставщику данных. Без аккаунтов. Без рекламы. Без сторонней аналитики.

Данные о тревогах: Ubilling aerial alerts (ubilling.net.ua).
```
