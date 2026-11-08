# Bee 🐝 — як отримати APK (для чайників)

1. Зайди на github.com → увійди → «+» → New repository → назва `bee` → Create.
2. Натисни «uploading an existing file». Розпакуй цей архів і перетягни ВСЕ вміст (папки app, файли build.gradle, settings.gradle, gradle.properties).
3. ВАЖЛИВО: папка `.github` прихована і може не завантажитись. Тому: Add file → Create new file → у назві напиши `.github/workflows/build.yml` → встав вміст файлу build.yml з архіву → Commit.
4. Відкрий вкладку **Actions** → «Build APK» → зачекай ~5 хв, поки з'явиться зелена галочка.
5. Відкрий збірку → внизу **Artifacts → BeeAnswer-apk** → завантаж, розпакуй zip → `app-debug.apk`.
6. Встанови APK на Samsung (дозволь встановлення з невідомих джерел).
7. Відкрий додаток Bee і виконай 3 кроки на екрані.
