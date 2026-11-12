# GawharaMissions 1.0.3

بلجن مهام يومية وأسبوعية مخصص لسيرفر GawharaSMP، مع مكافآت نقدية تلقائية عن طريق Vault Economy.

## المتطلبات
- Paper 26.3 build 170-beta (متوافق مع إصدار السيرفر الذي أرسلت سجله).
- Java 25 (مطلوب لبيئة Paper 26.3).
- Vault.
- بلجن اقتصاد يوفّر Vault Economy مثل EssentialsX Economy.

## المميزات
- مهام يومية تتجدد حسب المنطقة الزمنية في `settings.timezone` (الافتراضي `Africa/Cairo`).
- مهام أسبوعية تتجدد يوم الاثنين وفق ISO week.
- يختار البلجن مجموعة عشوائية من المهام لكل لاعب، ويحفظ نفس المهام والتقدم بعد إعادة التشغيل.
- واجهة GUI عبر `/missions`، مع `/missions daily` و`/missions weekly`.
- صرف المكافأة تلقائيًا إلى اللاعب عند إكمال المهمة من خلال Vault.
- حفظ بيانات اللاعبين في `plugins/GawharaMissions/players.yml`.
- تعديل عدد المهام، أنواعها، الأهداف، المكافآت، الأسماء، الأوصاف، والأيقونات من `config.yml`.

## أنواع المهام المدعومة
- `BREAK_BLOCK`: كسر بلوكات. يدعم `material` أو `materials`.
- `HARVEST_CROP`: حصاد محاصيل ناضجة فقط، ويُحتسب عند كسر المحصول بعد وصوله إلى مرحلة النمو الكامل.
- `KILL_MOB`: قتل كائنات. يدعم `entity` أو `entity-category: MONSTER`.
- `FISH`: صيد سمك باستخدام الصنارة.
- `CRAFT_ITEM`: تصنيع عنصر.
- `SMELT_ITEM`: استخراج العنصر المصهور من الفرن.
- `BREED_ANIMAL`: تزاوج الحيوانات بمساعدة اللاعب.
- `PLAY_MINUTES`: وقت اللعب بالدقائق.

## الأوامر
- `/missions` أو `/mission` أو `/tasks`: فتح قائمة المهام اليومية والأسبوعية.
- `/missions daily`: عرض المهام اليومية فقط.
- `/missions weekly`: عرض المهام الأسبوعية فقط.
- `/missions reload`: إعادة تحميل الإعدادات (صلاحية `gawharamissions.admin`).

## التثبيت
1. ثبّت Vault وبلجن اقتصاد يدعم Vault.
2. ضع `GawharaMissions-1.0.3.jar` في مجلد `plugins`.
3. أعد تشغيل السيرفر.
4. عدّل `plugins/GawharaMissions/config.yml` حسب رغبتك.
5. نفّذ `/missions reload` بعد تعديل الإعدادات.

## بناء JAR من السورس
يتطلب Maven وJava 25:

```bash
mvn clean package
```

سينتج الملف في `target/GawharaMissions-1.0.3.jar`.

يمكن أيضًا رفع المشروع إلى GitHub وتشغيل workflow الموجود في `.github/workflows/build.yml`؛ سيُرفع ملف JAR كـ Artifact.

## ملاحظات
- المكافآت تُصرف تلقائيًا مرة واحدة عند الوصول إلى الهدف.
- المهام المختارة والتقدم محفوظان في `players.yml`؛ لا تمسح الملف إلا إذا أردت إعادة ضبط بيانات المهام لجميع اللاعبين.
- المهام تُحتسب من الأحداث التي يستقبلها السيرفر؛ كسر البلوكات الملغى لا يُحتسب.
- `PLAY_MINUTES` يزيد مرة كل 60 ثانية أثناء وجود اللاعب متصلًا.

## إصلاح توافق Paper 26.3 في الإصدار 1.0.3
هذا الإصدار يصحح تواقيع JVM لاستدعاءات `ItemStack#setItemMeta(ItemMeta)` (ترجع `boolean`) و`HumanEntity#openInventory(Inventory)` (ترجع `InventoryView`) وفق واجهة Paper 26.3، ويستخدم التوقيع القياسي لـ Vault `Economy#depositPlayer(OfflinePlayer, double)`.

ملاحظة بناء: ملف JAR المرفق جرى تجميعه محليًا مع تواقيع compile-only المطابقة للواجهات المذكورة، لكن لم يتم تشغيل Maven ضد مستودع Paper/Vault من هذه البيئة بسبب عدم توفر اتصال المستودعات. استخدم GitHub Actions المرفق لتنفيذ Maven build الرسمي على Java 25، ثم جرّب الإصدار على نسخة احتياطية من السيرفر.


## إصلاح توافق Paper 26.3
هذا الإصدار `1.0.3` يبني السورس مباشرة ضد `Paper 26.3.build.170-beta` و`Java 25`. لا تستخدم JAR التجريبي من الإصدارات السابقة؛ لأن خطأ `NoSuchMethodError` السابق كان ناتجًا عن بناء غير مطابق لتواقيع Paper. بناء Maven الرسمي من هذا المشروع يعيد ترجمة استدعاءات `Player.openInventory(Inventory)` و`ItemStack.setItemMeta(ItemMeta)` وفق التواقيع الفعلية للمكتبة.
