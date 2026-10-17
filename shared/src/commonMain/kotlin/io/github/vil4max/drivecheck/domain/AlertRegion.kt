package io.github.vil4max.drivecheck.domain

enum class AlertRegion(val apiKey: String) {
    KyivCity("м. Київ"),
    Vinnytsia("Вінницька область"),
    Volyn("Волинська область"),
    Dnipropetrovsk("Дніпропетровська область"),
    Donetsk("Донецька область"),
    Zhytomyr("Житомирська область"),
    Zakarpattia("Закарпатська область"),
    Zaporizhzhia("Запорізька область"),
    IvanoFrankivsk("Івано-Франківська область"),
    KyivOblast("Київська область"),
    Kirovohrad("Кіровоградська область"),
    Luhansk("Луганська область"),
    Lviv("Львівська область"),
    Mykolaiv("Миколаївська область"),
    Odesa("Одеська область"),
    Poltava("Полтавська область"),
    Rivne("Рівненська область"),
    Sumy("Сумська область"),
    Ternopil("Тернопільська область"),
    Kharkiv("Харківська область"),
    Kherson("Херсонська область"),
    Khmelnytskyi("Хмельницька область"),
    Cherkasy("Черкаська область"),
    Chernivtsi("Чернівецька область"),
    Chernihiv("Чернігівська область");

    companion object {
        private val byApiKey = entries.associateBy { it.apiKey }

        fun fromApiKey(key: String): AlertRegion? = byApiKey[key]
    }
}
