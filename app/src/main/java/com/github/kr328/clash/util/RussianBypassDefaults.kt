package com.github.kr328.clash.util

import android.content.pm.PackageManager

/**
 * Базовый список российских приложений (банки/госуслуги/связь/маркетплейсы),
 * которым обычно нужен прямой выход в RU-сегмент в обход VPN. Используется
 * как преднабор для режима «Bypass — обход выбранных» в access-control.
 *
 * Список заведомо неполный — это стартовая точка, пользователь может
 * добавлять/убирать пакеты вручную через UI.
 */
object RussianBypassDefaults {
    val PACKAGES: Set<String> = linkedSetOf(
        // Банки
        "ru.sberbankmobile",
        "ru.sberbank.android",
        "ru.sberbank.spasibo",
        "ru.vtb24.mobilebanking.android",
        "ru.alfabank.mobile.android",
        "ru.alfabank.oavdo.amc",
        "com.idamob.tinkoff.android",
        "ru.tinkoff.mobile.tcs",
        "ru.tinkoff.investing",
        "ru.gazprombank.android.mobilebank.app",
        "ru.raiffeisennews",
        "ru.raiffeisen.mobile.new",
        "ru.psbank.mobile",
        "ru.rshb.dbo",
        "ru.rosbank.android",
        "ru.uralsib.mb",
        "ru.akbars.mobile",
        "logo.com.mbanking", // Открытие
        "ru.sovcombank.mobile",
        "ru.mkb.mobile",
        // Госуслуги / гос
        "ru.rostel",
        "ru.gosuslugi.app",
        "ru.gosuslugi.dom",
        "ru.fsspmobile",
        "ru.fns.billsreceipt",
        "ru.gibdd.gibddpay",
        "ru.mos.app", // Моя Москва
        "mos.dit.parkings",
        // Связь / операторы
        "ru.mts.mymts",
        "ru.beeline.services",
        "ru.megafon.mlk",
        "ru.tele2.mytele2",
        "ru.rt.mlk", // Ростелеком
        "ru.mts.music",
        "ru.mts.mtstv",
        // Транспорт / такси / доставка
        "ru.yandex.taxi",
        "ru.yandex.eda",
        "ru.yandex.market",
        "ru.yandex.searchplugin",
        "ru.yandex.mail",
        "ru.yandex.disk",
        "ru.yandex.maps",
        "com.yandex.browser",
        "com.yandex.metro",
        "ru.yandex.money", // ЮMoney
        "ru.yandex.weatherplugin",
        "com.delivery_club.Client",
        "ru.dodopizza.app",
        "com.burgerking.rbi.ru",
        "ru.kfc.kfc_android_app",
        // Маркетплейсы / шопинг
        "com.wildberries.ru",
        "com.ozon.app.android",
        "ru.aliexpress.buyer",
        "ru.mvideo.mobile",
        "ru.dns.shop",
        "com.ebay.kleinanzeigen.avito",
        "com.avito.android",
        "ru.cdek.sender",
        "ru.lenta.lenta",
        "ru.detmir.dmbonus",
        // Соц. сети / медиа / стриминг
        "com.vkontakte.android",
        "com.vk.im",
        "ru.ok.android",
        "ru.mail.mailapp",
        "ru.kinopoisk",
        "ru.kinopoisk.tv",
        "ru.ivi.client",
        "com.rutube.app",
        "ru.start.androidmobile",
        "ru.more.tv",
        "tv.okko.androidtv",
        "tv.okko.app",
        // Здоровье / госмедицина
        "ru.npd.android",
        "ru.gosuslugi.cabinet.health",
        "ru.minzdrav.gosuslugi.dms",
    )

    /** Returns the subset of [PACKAGES] that is currently installed. */
    fun installed(pm: PackageManager): Set<String> {
        val installed = pm.getInstalledPackages(0)
            .asSequence()
            .map { it.packageName }
            .toHashSet()
        return PACKAGES.filterTo(LinkedHashSet()) { it in installed }
    }
}
