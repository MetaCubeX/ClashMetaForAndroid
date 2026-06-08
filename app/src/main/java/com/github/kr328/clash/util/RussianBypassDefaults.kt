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
        "apiqa.ru.residentcabinetandroid", // ПИК-Комфорт
        "com.domonap.app",
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
        "ru.yandex.music",
        "ru.yandex.market",
        "ru.yandex.searchplugin",
        "ru.yandex.mail",
        "ru.yandex.disk",
        "ru.yandex.maps",
        "ru.yandex.yandexmaps",
        "ru.yandex.yandexnavi",
        "ru.yandex.translate",
        "ru.yandex.androidkeyboard",
        "com.yandex.browser",
        "com.yandex.bank",
        "com.yandex.iot",
        "com.yandex.metro",
        "ru.yandex.money", // ЮMoney
        "ru.yandex.weatherplugin",
        "com.delivery_club.Client",
        "ru.dodopizza.app",
        "com.burgerking.rbi.ru",
        "ru.kfc.kfc_android_app",
        "ru.kfc.kfc_delivery",
        "ru.foodfox.client",
        "com.logistic.sdek",
        "ru.dublgis.dgismobile",
        "ru.tutu.train",
        "com.taxsee.taxsee",
        "ru.mosparking.appnew",
        "parking.mos.ru",
        "ru.gosuslugi.auto",
        "com.punicapp.whoosh",
        // Маркетплейсы / шопинг
        "com.wildberries.ru",
        "com.ozon.app.android",
        "ru.ozon.app.android",
        "ru.ozon.select",
        "ru.ozon.fintech.finance",
        "ru.megamarket.marketplace",
        "ru.instamart",
        "ru.sbcs.store",
        "ru.aliexpress.buyer",
        "ru.mvideo.mobile",
        "ru.dns.shop",
        "com.ebay.kleinanzeigen.avito",
        "com.avito.android",
        "ru.cdek.sender",
        "ru.lenta.lenta",
        "ru.detmir.dmbonus",
        "ru.tander.magnit",
        "ru.vkusvill",
        "ru.pyaterochka.app",
        "ru.pyaterochka.app.browser",
        "ru.x5.p5client",
        "ru.x5club.android",
        "ru.bestprice.fixprice",
        "ru.letu",
        "ru.sportmaster.app",
        "ru.beru.android",
        "ru.lamoda.android",
        "com.octopod.russianpost.client.android",
        // Соц. сети / медиа / стриминг
        "com.vkontakte.android",
        "com.vk.im",
        "com.vk.vkvideo",
        "ru.vk.store",
        "ru.oneme.app",
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
        "com.zvooq.openplay", // Звук
        "gpm.tnt_premier", // PREMIER
        // Здоровье / госмедицина
        "ru.npd.android",
        "com.gnivts.selfemployed",
        "ru.gosuslugi.cabinet.health",
        "ru.gosuslugi.goskey",
        "ru.gosuslugi.culture",
        "ru.minzdrav.gosuslugi.dms",
        "com.programmisty.emiasapp", // ЕМИАС.ИНФО
        "ru.emias.telemed", // ЕМИАС:Телемедицина
        // Работа / сервисы
        "ru.hh.android",
        "ru.kwork.app",
        "ru.aorr.tkpclient",
        "ru.nspk.mirpay",
        "com.bitrix24.android",
        "com.bitrixsoft.otp", // Битрикс OTP
    )

    /** Returns the subset of [PACKAGES] that is currently installed. */
    fun installed(pm: PackageManager): Set<String> {
        val installed = pm.getInstalledPackages(0)
            .asSequence()
            .map { it.packageName }
            .toHashSet()
        return installed.asSequence()
            .filter(::isRussianPackage)
            .plus(PACKAGES.asSequence().filter { it in installed })
            .distinct()
            .toCollection(LinkedHashSet())
    }

    private fun isRussianPackage(packageName: String): Boolean =
        packageName.startsWith("ru.") ||
            packageName.startsWith("com.yandex.") ||
            packageName.startsWith("com.vk.") ||
            packageName == "com.vkontakte.android" ||
            packageName == "com.wildberries.ru" ||
            packageName == "com.ozon.app.android" ||
            packageName == "com.avito.android" ||
            packageName == "com.logistic.sdek" ||
            packageName == "com.idamob.tinkoff.android" ||
            packageName == "com.octopod.russianpost.client.android" ||
            packageName == "com.taxsee.taxsee" ||
            packageName == "com.gnivts.selfemployed"
}
