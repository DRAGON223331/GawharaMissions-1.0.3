# GawharaMissions 1.1.0 — GawharaCoins Integration

تم ربط المكافآت بـ GawharaCoins بدل Vault: **كل مهمة مكتملة تمنح 1 GC** افتراضيًا. لا يتم منح الدولار للمهمة. غيّر القيمة من `coins.per-mission` في `config.yml` إذا احتجت.

- يحتفظ البلجن ببيانات التقدم في `players.yml`.
- لكل مكافأة transaction key فريد: نفس المهمة في نفس اللاعب/الفترة لا تُدفع مرتين.
- `softdepend: [GawharaCoins]` يضمن تحميل GawharaCoins قبله إن كان موجودًا.
- GUI المهام يعرض `1 GC` كمكافأة.

أوامر: `/missions`, `/missions daily`, `/missions weekly`, `/missions reload`.

البناء: Java 25 وPaper 26.3 build 170-beta عبر GitHub Actions → Build GawharaMissions JAR. ثبت GawharaCoins أولًا ثم Missions.
