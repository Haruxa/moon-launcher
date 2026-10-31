package dev.margymc.launcher

/**
 * Простейшая локализация без ресурсов: строки пишутся по-русски в коде,
 * а [t] отдаёт английский перевод. Значение языка хранится в [Prefs.lang].
 */
object I18n {
    var lang: String = "en"

    val languages = listOf("en" to "English", "ru" to "Русский")

    fun isRu() = lang == "ru"

    fun t(s: String): String = if (isRu()) s else EN[s] ?: s

    fun pick(en: String, ru: String) = if (isRu()) ru else en

    private val EN = mapOf(
        // ---- вкладки
        "Моды" to "Mods",
        "Управление" to "Controls",
        "Сборка" to "Build",
        "Ещё" to "More",
        // ---- моды Horizon
        "Моды Horizon" to "Horizon mods",
        "Каталог модов и нативных расширений для игры 26.3" to "Mods and native extensions for game 26.3",
        "Готовые" to "Ready",
        "Нативные" to "Native",
        "Установить" to "Install",
        "Собрать APK" to "Build APK",
        "скачиваю…" to "downloading…",
        "установлен" to "installed",
        "нет версии для 26.3" to "no version for 26.3",
        "нет версии" to "no version",
        "Нужен токен GitHub — вкладка «Ещё»" to "GitHub token required — see “More” tab",
        "запускаю сборку…" to "starting build…",
        // ---- Modrinth
        "Пакеты — как есть, Java-моды переносим данными в .hmod" to
            "Packs install as is; Java mods are ported as data into .hmod",
        "Поиск" to "Search",
        "нужен анализ" to "needs analysis",
        "Анализ" to "Analyze",
        "В .hmod" to "To .hmod",
        "Ставить" to "Install",
        "качаю jar для анализа…" to "downloading jar…",
        "собираю .hmod…" to "building .hmod…",
        "нет файла мода" to "mod file is missing",
        "сначала анализ" to "run analysis first",
        "нет сборки под 26.x" to "no build for 26.x",
        "установлено в " to "installed to ",
        "файлов, пропущено " to " files, skipped ",
        "Ресурспаки" to "Resource packs",
        "Шейдеры" to "Shaders",
        "Датапаки" to "Data packs",
        // ---- уровни переноса
        "Полностью портируется" to "Ports fully",
        "Наполовину портируется" to "Ports partially",
        "Не нужен в Horizon" to "Not needed in Horizon",
        "Запустить нельзя" to "Cannot run",
        "полный порт" to "full port",
        "частично" to "partial",
        "не нужен" to "not needed",
        "только Java" to "Java only",
        // ---- сборка
        "Сборка APK" to "APK build",
        "Нативные моды (.hnmod) вшиваются в игру в GitHub Actions" to
            "Native mods (.hnmod) are compiled into the game by GitHub Actions",
        "Как это работает" to "How it works",
        "Лаунчер кладёт рецепт сборки в твой приватный репозиторий, запускает " +
            "GitHub Actions и через 20–40 минут присылает готовый APK. " +
            "На телефоне ничего не компилируется." to
            "The launcher uploads a build recipe to your private repository, triggers " +
                "GitHub Actions and delivers a ready APK in 20–40 minutes. " +
                "Nothing is compiled on the phone.",
        "Собирается…" to "Building…",
        "Собрать APK с модами" to "Build APK with mods",
        "Сначала вставь токен GitHub на вкладке «Ещё»" to "Paste a GitHub token on the “More” tab first",
        "Идёт сборка — можно свернуть приложение" to "Building — you can minimize the app",
        // ---- настройки
        "Подключение, папки и права" to "Account, folders and permissions",
        "Токен (repo + workflow)" to "Token (repo + workflow)",
        "Репозиторий сборок (owner/name)" to "Build repository (owner/name)",
        "Папка игры" to "Game folder",
        "Путь" to "Path",
        "Моды: " to "Mods: ",
        "Сохранить" to "Save",
        "Сохранено" to "Saved",
        "Права" to "Permissions",
        "Лаунчер пишет файлы в папку игры и отдаёт APK системному установщику." to
            "The launcher writes into the game folder and hands APKs to the system installer.",
        "Все файлы" to "All files",
        "Установка APK" to "Install APK",
        "Язык" to "Language",
        "Интерфейс лаунчера" to "Launcher interface",
        // ---- экранное управление
        "Экранное управление" to "Touch controls",
        "Раскладка кнопок в игре" to "On-screen button layout",
        "Профилей нет — нажми «Новый», создам стандартный." to
            "No profiles yet — tap “New” and I will create the default one.",
        "Новый" to "New",
        "Копия" to "Duplicate",
        "Правка" to "Edit",
        "Удалить активный" to "Delete active",
        "кнопок" to "buttons",
        "Профиль" to "Profile",
        "Выйти" to "Exit",
        "Добавить кнопку" to "Add button",
        "Удалить" to "Delete",
        "Подпись" to "Label",
        "Настройки кнопки" to "Button settings",
        "Действие" to "Action",
        "Режим" to "Mode",
        "прицел" to "aim",
        "тап" to "tap",
        "штатная кнопка" to "builtin",
        "клавиша" to "key",
        "удержание" to "hold",
        "макрос" to "macro",
        "команда в чат" to "chat command",
        "другой профиль" to "other profile",
        "Клавиши через пробел (shift e)" to "Keys separated by space (shift e)",
        "Команда (/gamemode creative)" to "Command (/gamemode creative)",
        "Клавиша (e, space, shift)" to "Key (e, space, shift)",
        "Игра читает эти файлы при запуске" to "The game reads these files on launch",
        "активный профиль" to "active profile",
        "нет" to "none",
        "Стандартный" to "Default",
        "Кнопка" to "Button",
        "Изменения не сохранены" to "Unsaved changes",
        "Перетаскивай кнопки по экрану" to "Drag buttons around the screen",
        // ---- общее
        "Ошибка: " to "Error: ",
        "Готово" to "Done",
    )
}
