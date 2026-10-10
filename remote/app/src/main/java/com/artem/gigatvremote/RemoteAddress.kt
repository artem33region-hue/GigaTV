package com.artem.gigatvremote

import okhttp3.HttpUrl

/** Accept an IP/host, including bracketed or bare IPv6, without parsing it as a URL. */
internal fun remoteAddress(input: String): HttpUrl {
    val host = input.trim().removeSurrounding("[", "]")
    require(host.isNotBlank()) { "Введите IP телевизора" }
    require('%' !in host) { "Для локального IPv6 укажите IPv4-адрес телевизора" }
    return HttpUrl.Builder().scheme("http").host(host).port(TV_PORT).build()
}
